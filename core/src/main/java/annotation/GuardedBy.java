package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Denotes that the annotated method or field can only be accessed when holding the referenced lock.
 * <p>
 * Example:
 * <pre>
 * final Object objectLock = new Object();
 *
 * {@literal @}GuardedBy("objectLock")
 * volatile Object object;
 *
 * Object getObject() {
 *     synchronized (objectLock) {
 *         if (object == null) {
 *             object = new Object();
 *         }
 *     }
 *     return object;
 * }</pre>
 */
@Retention(CLASS)
@Target({FIELD, METHOD})
public @interface GuardedBy {
    String value();
}
