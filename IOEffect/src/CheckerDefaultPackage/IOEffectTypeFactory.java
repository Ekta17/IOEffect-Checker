package CheckerDefaultPackage;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;

import CheckerDefaultPackage.qual.AlwaysNoIO;
import CheckerDefaultPackage.qual.IO;
import CheckerDefaultPackage.qual.IOEffect;
import CheckerDefaultPackage.qual.IOPackage;
import CheckerDefaultPackage.qual.IOType;
import CheckerDefaultPackage.qual.NoIOEffect;
import CheckerDefaultPackage.qual.NoIOType;

public class IOEffectTypeFactory extends BaseAnnotatedTypeFactory {

	protected final boolean debugSpew;

    public IOEffectTypeFactory(BaseTypeChecker checker, boolean spew) {
        // use true to enable flow inference, false to disable it
        super(checker, false);

        debugSpew = spew;
        this.postInit();
    }
	
    public ExecutableElement findJavaOverride(ExecutableElement overrider, TypeMirror parentType) {
        if (parentType.getKind() != TypeKind.NONE) {
            if (debugSpew) {
                System.err.println("Searching for overridden methods from " + parentType);
            }

            TypeElement overriderClass = (TypeElement) overrider.getEnclosingElement();
            TypeElement elem = (TypeElement) ((DeclaredType) parentType).asElement();
            if (debugSpew) {
                System.err.println("necessary TypeElements acquired: " + elem);
            }

            for (Element e : elem.getEnclosedElements()) {
            	//System.out.println("element e = "+e);
                if (debugSpew) {
                    System.err.println("Considering element " + e);
                }
                if (e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR) {
                    ExecutableElement ex = (ExecutableElement) e;
                    boolean overrides = elements.overrides(overrider, ex, overriderClass);
                    if (overrides) {
                        return ex;
                    }
                }
            }
            if (debugSpew) {
                System.err.println("Done considering elements of " + parentType);
            }
        }
        return null;
    }
    
    public boolean isIOType(TypeElement cls) {
        if (debugSpew) {
            System.err.println(" isIOType(" + cls + ")");
        }
        boolean targetClassIOP = fromElement(cls).hasAnnotation(IO.class);
        AnnotationMirror targetClassIOTypeP = getDeclAnnotation(cls, IOType.class);
        AnnotationMirror targetClassNoIOTypeP = getDeclAnnotation(cls, NoIOType.class);

        if (targetClassNoIOTypeP != null) {
            return false; // explicitly marked not a IO type
        }

        boolean hasIOTypeDirectly = (targetClassIOP || targetClassIOTypeP != null);

        if (hasIOTypeDirectly) {
            return true;
        }

        // Anon inner classes should not inherit the package annotation, since
        // they're so often used for closures to run async on background
        // threads.
        if (isAnonymousType(cls)) {
            return false;
        }

        boolean targetClassNoIOP = fromElement(cls).hasAnnotation(AlwaysNoIO.class);
        if (targetClassNoIOP) {
            return false; // explicitly annotated otherwise
        }

        // Look for the package
        Element packageP = ElementUtils.enclosingPackage(cls);

        if (packageP != null) {
            if (debugSpew) {
                System.err.println("Found package " + packageP);
            }
            if (getDeclAnnotation(packageP, IOPackage.class) != null) {
                if (debugSpew) {
                    System.err.println("Package " + packageP + " is annotated @IOPackage");
                }
                return true;
            }
        }

        return false;
    }
    
    private static boolean isAnonymousType(TypeElement elem) {
        return elem.getSimpleName().length() == 0;
    }
    
    public MainEffect getDeclaredEffect(ExecutableElement methodElt) {
        if (debugSpew) {
            System.err.println("begin mayHaveIOEffect(" + methodElt + ")");
        }
        AnnotationMirror targetIOP = getDeclAnnotation(methodElt, IOEffect.class);
        AnnotationMirror targetNoIOP = getDeclAnnotation(methodElt, NoIOEffect.class);
        TypeElement targetClassElt = (TypeElement) methodElt.getEnclosingElement();

        if (debugSpew) {
            System.err.println("targetClassElt found");
        }

        // Short-circuit if the method is explicitly annotated
        if (targetNoIOP != null) {
            if (debugSpew) {
                System.err.println("Method marked @NoIOEffect");
            }
            return new MainEffect(NoIOEffect.class);
        } else if (targetIOP != null) {
            if (debugSpew) {
                System.err.println("Method marked @IOEffect");
            }
            return new MainEffect(IOEffect.class);
        }

        // The method is not explicitly annotated, so check class and package annotations,
        // and supertype effects if in an anonymous inner class

        if (isIOType(targetClassElt)) {
            // Already checked, no explicit @NoIOEffect annotation
            return new MainEffect(IOEffect.class);
        }

        // Anonymous inner types should just get the effect of the parent by
        // default, rather than annotating every instance. Unless it's
        // implementing a polymorphic supertype, in which case we still want the
        // developer to be explicit.
        if (isAnonymousType(targetClassElt)) {
            boolean canInheritParentEffects = true; // Refine this for polymorphic parents
            //DeclaredType directSuper = (DeclaredType) targetClassElt.getSuperclass();
            //TypeElement superElt = (TypeElement) directSuper.asElement();
            // Anonymous subtypes of polymorphic classes other than object can't inherit
           
            if (canInheritParentEffects) {
                MainEffect.EffectRange r = findInheritedEffectRange(targetClassElt, methodElt);
                return (r != null ? MainEffect.min(r.min, r.max) : new MainEffect(NoIOEffect.class));
            }
        }

        return new MainEffect(NoIOEffect.class);
    }
    
    public MainEffect.EffectRange findInheritedEffectRange(
            TypeElement declaringType, ExecutableElement overridingMethod) {
        return findInheritedEffectRange(declaringType, overridingMethod, false, null);
    }
    
    public MainEffect.EffectRange findInheritedEffectRange(
            TypeElement declaringType,
            ExecutableElement overridingMethod,
            boolean issueConflictWarning,
            Tree errorNode) {
        assert (declaringType != null);
        ExecutableElement io_override = null;
        ExecutableElement noIO_override = null;

       // System.out.println("declaringType = "+declaringType/*+"\t overridingMethod="+overridingMethod+"\t errorNode="+errorNode*/);
        
        // We must account for explicit annotation, type declaration annotations, and package annotations
        boolean isIO =
                (getDeclAnnotation(overridingMethod, IOEffect.class) != null
                                || isIOType(declaringType))
                        && getDeclAnnotation(overridingMethod, NoIOEffect.class) == null;

        // TODO: We must account for @IO and @AlwaysNoIO annotations for extends
        // and implements clauses, and do the proper substitution of @Poly effects and quals!
        // List<? extends TypeMirror> interfaces = declaringType.getInterfaces();
        TypeMirror superclass = declaringType.getSuperclass();
        //System.out.println("SuperClass = "+superclass);
        while (superclass != null && superclass.getKind() != TypeKind.NONE) {
            ExecutableElement overrides = findJavaOverride(overridingMethod, superclass);
            System.out.println("overrides 1st loop = "+overrides);
            if (overrides != null) {
            	MainEffect eff = getDeclaredEffect(overrides);
            	//System.out.println("Overriden effect = "+eff);
                assert (eff != null);
                if (eff.isNoIO()) {
                    // found a noIO override
                    noIO_override = overrides;
                    if (isIO && issueConflictWarning) {
                        checker.report(
                                Result.failure(
                                        "override.effect.invalid",
                                        overridingMethod,
                                        declaringType,
                                        noIO_override,
                                        superclass),
                                errorNode);
                    }
                } else if (eff.isIO()) {
                    // found a io override
                    io_override = overrides;
                } 
                }
            DeclaredType decl = (DeclaredType) superclass;
            superclass = ((TypeElement) decl.asElement()).getSuperclass();
        }

        AnnotatedTypeMirror.AnnotatedDeclaredType annoDecl = fromElement(declaringType);
        System.out.println("annoDEcl = "+annoDecl);
        for (AnnotatedTypeMirror.AnnotatedDeclaredType ty : annoDecl.directSuperTypes()) {
            ExecutableElement overrides =
                    findJavaOverride(overridingMethod, ty.getUnderlyingType());
            System.out.println("overrides in 2nd loop = "+overrides);
            if (overrides != null) {
                MainEffect eff = getDeclaredEffect(overrides);
                if (eff.isNoIO()) {
                    // found a noIO override
                    noIO_override = overrides;
                    if (isIO && issueConflictWarning) {
                        checker.report(
                                Result.failure(
                                        "override.effect.invalid",
                                        overridingMethod,
                                        declaringType,
                                        noIO_override,
                                        ty),
                                errorNode);
                    }
                   } else if (eff.isIO()) {
                    // found a io override
                    io_override = overrides;
                }
               }
        }

        // We don't need to issue warnings for inheriting from poly and a concrete effect.
        if (io_override != null && noIO_override != null && issueConflictWarning) {
            // There may be more than two parent methods, but for now it's
            // enough to know there are at least 2 in conflict
            checker.report(
                    Result.warning(
                            "override.effect.warning.inheritance",
                            overridingMethod,
                            declaringType,
                            io_override.toString(),
                            io_override.getEnclosingElement().asType().toString(),
                            noIO_override.toString(),
                            noIO_override.getEnclosingElement().asType().toString()),
                    errorNode);
        }

        MainEffect min=null;
        
        if(noIO_override!=null)
        	min=new MainEffect(NoIOEffect.class);
              
        MainEffect max=null;
        
        if(io_override!=null)
        	max=new MainEffect(IOEffect.class);
        
        if (debugSpew) {
            System.err.println(
                    "Found "
                            + declaringType
                            + "."
                            + overridingMethod
                            + " to have inheritance pair ("
                            + min
                            + ","
                            + max
                            + ")");
        }

        if (min == null && max == null) {
            return null;
        } else {
            return new MainEffect.EffectRange(min, max);
        }
    }
    
    @Override
    protected TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(super.createTreeAnnotator(), new IOEffectTreeAnnotator());
    }

    private class IOEffectTreeAnnotator extends TreeAnnotator {

    	IOEffectTreeAnnotator() {
            super(IOEffectTypeFactory.this);
        }

        public boolean hasExplicitIOEffect(ExecutableElement methElt) {
            return IOEffectTypeFactory.this.getDeclAnnotation(methElt, IOEffect.class) != null;
        }

        public boolean hasExplicitNoIOEffect(ExecutableElement methElt) {
            return IOEffectTypeFactory.this.getDeclAnnotation(methElt, NoIOEffect.class) != null;
        }

        public boolean hasExplicitEffect(ExecutableElement methElt) {
            return hasExplicitIOEffect(methElt)
                    || hasExplicitNoIOEffect(methElt);
        }

        @Override
        public Void visitMethod(MethodTree node, AnnotatedTypeMirror type) {
            AnnotatedTypeMirror.AnnotatedExecutableType methType =
                    (AnnotatedTypeMirror.AnnotatedExecutableType) type;
            MainEffect e = getDeclaredEffect(methType.getElement());
            TypeElement cls = (TypeElement) methType.getElement().getEnclosingElement();

            // STEP 1: Get the method effect annotation
            if (!hasExplicitEffect(methType.getElement())) {
                // TODO: This line does nothing!
                // AnnotatedTypeMirror.addAnnotation silently ignores non-qualifier annotations!
                // We should be digging up the /declaration/ of the method, and annotating that.
                methType.addAnnotation(e.getAnnot());
            }

            // STEP 2: Fix up the method receiver annotation
            AnnotatedTypeMirror.AnnotatedDeclaredType receiverType = methType.getReceiverType();
            if (receiverType != null
                    && !receiverType.isAnnotatedInHierarchy(
                            AnnotationUtils.fromClass(elements, IO.class))) {
                receiverType.addAnnotation(fromElement(cls).hasAnnotation(IO.class)
                                        ? IO.class
                                        : AlwaysNoIO.class);
            }
            return super.visitMethod(node, type);
        }
    }
}
