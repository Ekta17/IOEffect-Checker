package CheckerDefaultPackage;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
//import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.source.SupportedLintOptions;

//@StubFiles({"org-eclipse.astub", "org-osgi.astub", "org-swtchart.astub"})
@SupportedLintOptions({"debugSpew"})
public class IOEffectChecker extends BaseTypeChecker {
	
	@Override
	protected BaseTypeVisitor<?> createSourceVisitor() {	
		return new IOEffectVisitor(this);
    }
}
