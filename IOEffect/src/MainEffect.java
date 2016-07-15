import java.lang.annotation.Annotation;

import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;

import qual.IOEffect;
import qual.NoIOEffect;

public final class MainEffect {
    
	private final Class<? extends Annotation> annotClass;

    public MainEffect(Class<? extends Annotation> cls) {
        assert (cls.equals(IOEffect.class)
                || cls.equals(NoIOEffect.class));
        annotClass = cls;
    }

    public static boolean LE(MainEffect left, MainEffect right) {
        assert (left != null && right != null);
        boolean leftBottom = left.annotClass.equals(NoIOEffect.class);
        boolean rightTop = right.annotClass.equals(IOEffect.class);
        return leftBottom || rightTop || left.annotClass.equals(right.annotClass);
    }

    public static MainEffect min(MainEffect l, MainEffect r) {
        if (LE(l, r)) {
            return l;
        } else {
            return r;
        }
    }

    public static final class EffectRange {
        public final MainEffect min, max;

        public EffectRange(MainEffect min, MainEffect max) {
            assert (min != null || max != null);
            // If one is null, fill in with the other
            this.min = (min != null ? min : max);
            this.max = (max != null ? max : min);
        }
    }

    public boolean isNoIO() {
        return annotClass.equals(NoIOEffect.class);
    }

    public boolean isIO() {
        return annotClass.equals(IOEffect.class);
    }

    public Class<? extends Annotation> getAnnot() {
        return annotClass;
    }

    @SideEffectFree
    @Override
    public String toString() {
        return annotClass.getSimpleName();
    }

    public boolean equals(MainEffect e) {
        return annotClass.equals(e.annotClass);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MainEffect) {
            return this.equals((MainEffect) o);
        } else {
            return super.equals(o);
        }
    }

    @Pure
    @Override
    public int hashCode() {
        return 31 + annotClass.hashCode();
    }
}
