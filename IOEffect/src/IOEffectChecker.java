import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.source.SupportedLintOptions;

@StubFiles({"org-eclipse.astub", "org-osgi.astub", "org-swtchart.astub"})
@SupportedLintOptions({"debugSpew"})
public class IOEffectChecker extends BaseTypeChecker {}
