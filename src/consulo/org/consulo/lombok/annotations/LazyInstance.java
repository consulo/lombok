package org.consulo.lombok.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author VISTALL
 * @since 28.04.14
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
public @interface LazyInstance
{
	boolean notNull() default true;
}
