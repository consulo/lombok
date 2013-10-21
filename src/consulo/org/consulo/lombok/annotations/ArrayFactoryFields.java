package org.consulo.lombok.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author VISTALL
 * @since 15:07/21.10.13
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface ArrayFactoryFields
{
}
