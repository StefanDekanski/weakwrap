package com.stefandekanski.weakwrap.anotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//TODO add 'wrapOnly' boolean option, default false
//TODO only wrap will not extend or implement original class
//TODO this can be used for 'overriding' final methods
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface WeakWrap {
}
