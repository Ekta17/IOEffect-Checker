package CheckerDefaultPackage;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
//import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.source.SupportedLintOptions;

@SupportedLintOptions({"debugSpew"})
public class IOEffectChecker extends BaseTypeChecker {
	
	@Override
	protected BaseTypeVisitor<?> createSourceVisitor() {	
		return new IOEffectVisitor(this);
    }
}
