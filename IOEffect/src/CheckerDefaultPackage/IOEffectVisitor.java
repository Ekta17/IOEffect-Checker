package CheckerDefaultPackage;

import java.util.Collections;
import java.util.Set;
import java.util.Stack;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;

import CheckerDefaultPackage.qual.AlwaysNoIO;
import CheckerDefaultPackage.qual.IO;
import CheckerDefaultPackage.qual.IOEffect;
import CheckerDefaultPackage.qual.NoIOEffect;

public class IOEffectVisitor extends BaseTypeVisitor<IOEffectTypeFactory> {

	protected final boolean debugSpew;

    // effStack and currentMethods should always be the same size.
    protected final Stack<MainEffect> effStack;
    protected final Stack<MethodTree> currentMethods;

    public IOEffectVisitor(BaseTypeChecker checker) {
        super(checker);
        debugSpew = checker.getLintOption("debugSpew", false);
        if (debugSpew) {
            System.err.println("Running IOEffectVisitor");
        }
        effStack = new Stack<MainEffect>();
        currentMethods = new Stack<MethodTree>();
    }

    @Override
    protected IOEffectTypeFactory createTypeFactory() {
        return new IOEffectTypeFactory(checker, debugSpew);
    }
    
    @Override
    protected void checkMethodInvocability(
            AnnotatedExecutableType method, MethodInvocationTree node) {
    }

    @Override
    protected boolean checkOverride(
            MethodTree overriderTree,
            AnnotatedTypeMirror.AnnotatedDeclaredType enclosingType,
            AnnotatedTypeMirror.AnnotatedExecutableType overridden,
            AnnotatedTypeMirror.AnnotatedDeclaredType overriddenType,
            Void p) {

    	return true;
    }

    @Override
    protected Set<? extends AnnotationMirror> getExceptionParameterLowerBoundAnnotations() {
    	return Collections.singleton(AnnotationUtils.fromClass(elements, AlwaysNoIO.class));
    }
    
    @Override
    public boolean isValidUse(
            AnnotatedTypeMirror.AnnotatedDeclaredType declarationType,
            AnnotatedTypeMirror.AnnotatedDeclaredType useType,
            Tree tree) {
        boolean ret =
                useType.hasAnnotation(AlwaysNoIO.class)
                        || (useType.hasAnnotation(IO.class)
                                && declarationType.hasAnnotation(IO.class));
        if (debugSpew && !ret) {
            System.err.println("use: " + useType);
            System.err.println("use noIO: " + useType.hasAnnotation(AlwaysNoIO.class));
            System.err.println("use io: " + useType.hasAnnotation(IO.class));
            System.err.println(
                    "declaration noIO: " + declarationType.hasAnnotation(AlwaysNoIO.class));
            System.err.println("declaration io: " + declarationType.hasAnnotation(IO.class));
            System.err.println("declaration: " + declarationType);
        }
        return ret;
    }

    // Check that the invoked effect is <= permitted effect (effStack.peek())
    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        if (debugSpew) {
            System.err.println("For invocation " + node + " in " + currentMethods.peek().getName());
        }

        // Target method annotations
        ExecutableElement methodElt = TreeUtils.elementFromUse(node);
        if (debugSpew) {
            System.err.println("methodElt found");
        }

        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        if (callerTree == null) {

        	if (debugSpew) {
                System.err.println("No enclosing method: likely static initializer");
            }
            return super.visitMethodInvocation(node, p);
        }
        if (debugSpew) {
            System.err.println("callerTree found");
        }

        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        if (debugSpew) {
            System.err.println("callerElt found");
        }

        MainEffect targetEffect = atypeFactory.getDeclaredEffect(methodElt);
        MainEffect callerEffect = atypeFactory.getDeclaredEffect(callerElt);

        assert (currentMethods.peek() == null || callerEffect.equals(effStack.peek()));

        if (!MainEffect.LE(targetEffect, callerEffect)) {
            checker.report(Result.failure("call.invalid.io", targetEffect, callerEffect), node);
            if (debugSpew) {
                System.err.println("Issuing error for node: " + node);
            }
        }
        if (debugSpew) {
            System.err.println(
                    "Successfully finished main non-recursive checkinv of invocation " + node);
        }

        return super.visitMethodInvocation(node, p);
    }
    
    @Override
    public Void visitMethod(MethodTree node, Void p) {

    	ExecutableElement methElt = TreeUtils.elementFromDeclaration(node);
        if (debugSpew) {
            System.err.println("\nVisiting method " + methElt);
        }


        assert (methElt != null);
        AnnotationMirror targetIOP = atypeFactory.getDeclAnnotation(methElt, IOEffect.class);

        AnnotationMirror targetNoIOP = atypeFactory.getDeclAnnotation(methElt, NoIOEffect.class);

        TypeElement targetClassElt = (TypeElement) methElt.getEnclosingElement();

        if (targetIOP != null && atypeFactory.isIOType(targetClassElt)) {
            checker.report(Result.warning("effects.redundant.iotype"), node);
        }


        @SuppressWarnings("unused") 
        MainEffect.EffectRange range =
                atypeFactory.findInheritedEffectRange(
                        ((TypeElement) methElt.getEnclosingElement()), methElt, true, node);
        if (targetIOP == null && targetNoIOP == null) {
            atypeFactory
                    .fromElement(methElt)
                    .addAnnotation(atypeFactory.getDeclaredEffect(methElt).getAnnot());
        }


        currentMethods.push(node);
        effStack.push(atypeFactory.getDeclaredEffect(methElt));
        if (debugSpew) {
            System.err.println(
                    "Pushing " + effStack.peek() + " onto the stack when checking " + methElt);
        }

        Void ret = super.visitMethod(node, p);
        currentMethods.pop();
        effStack.pop();
        return ret;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        return super.visitMemberSelect(node, p);
    }
    	
    @Override
    public Void visitClass(ClassTree node, Void p) {
        currentMethods.push(null);// static int x=dosomething();
        effStack.push(new MainEffect(IOEffect.class));
        Void ret = super.visitClass(node, p);
        currentMethods.pop();
        effStack.pop();
        return ret;
    }
    
    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        if (debugSpew) {
            System.err.println("For constructor " + node + " in " + currentMethods.peek().getName());
        }

        // Target method annotations
        ExecutableElement methodElt = TreeUtils.elementFromUse(node);
        if (debugSpew) {
            System.err.println("methodElt found");
        }

        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        if (callerTree == null) {

            if (debugSpew) {
                System.err.println("No enclosing method: likely static initializer");
            }
            return super.visitNewClass(node, p);
        }
        if (debugSpew) {
            System.err.println("callerTree found");
        }

        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        if (debugSpew) {
            System.err.println("callerElt found");
        }

        MainEffect targetEffect = atypeFactory.getDeclaredEffect(methodElt);
        MainEffect callerEffect = atypeFactory.getDeclaredEffect(callerElt);

        assert (currentMethods.peek() == null || callerEffect.equals(effStack.peek()));

        if (!MainEffect.LE(targetEffect, callerEffect)) {
            checker.report(Result.failure("constructor.call.invalid", targetEffect, callerEffect), node);
            if (debugSpew) {
                System.err.println("Issuing error for node: " + node);
            }
        }
        if (debugSpew) {
            System.err.println(
                    "Successfully finished main non-recursive checkinv of invocation " + node);
        }

        return super.visitNewClass(node, p);
    }
}
