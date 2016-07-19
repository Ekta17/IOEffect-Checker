package CheckerDefaultPackage;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.source.SupportedLintOptions;

@StubFiles({"org-eclipse.astub", "org-osgi.astub", "org-swtchart.astub"})
@SupportedLintOptions({"debugSpew"})
public class IOEffectChecker extends BaseTypeChecker {
	
	@Override
	protected BaseTypeVisitor<?> createSourceVisitor() {
        /*// Try to reflectively load the visitor.
        Class<?> checkerClass = this.getClass();

        while (checkerClass != BaseTypeChecker.class) {
            final String classToLoad =
                    checkerClass
                            .getName()
                            .replace("Checker", "IOEffectVisitor")
                            .replace("Subchecker", "IOEffectVisitor");
            BaseTypeVisitor<?> result =
                    invokeConstructorFor(
                            classToLoad,
                            new Class<?>[] {BaseTypeChecker.class},
                            new Object[] {this});
            if (result != null) {
                return result;
            }
            checkerClass = checkerClass.getSuperclass();
        }

        // If a visitor couldn't be loaded reflectively, return the default.
        return new BaseTypeVisitor<BaseAnnotatedTypeFactory>(this);*/
		
		return new IOEffectVisitor(this);
    }
}
