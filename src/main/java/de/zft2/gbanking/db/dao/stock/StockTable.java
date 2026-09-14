package de.zft2.gbanking.db.dao.stock;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
public @interface StockTable {

	String name();

	String idColumn() default "id";

	String parentColumn() default "";

	boolean generatedId() default true;

	boolean createdAt() default false;

	boolean updatedAt() default false;

	StockWriteMode writeMode() default StockWriteMode.MUTABLE;
}
