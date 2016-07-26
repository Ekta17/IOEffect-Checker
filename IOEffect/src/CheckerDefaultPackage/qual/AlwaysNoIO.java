package CheckerDefaultPackage.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.SubtypeOf;

import org.checkerframework.framework.qual.LiteralKind;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

@SubtypeOf({IO.class})
@DefaultQualifierInHierarchy
@ImplicitFor(literals = LiteralKind.NULL)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface AlwaysNoIO {}

