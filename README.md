Weak Wrap
=========

Tiny WeakReference wrapper, for Android and Java. Generates the usual (repetitive) code for working with WeakReferences.

Generates tiny WeakWrap<ClassName> class with typical boilerplate code check.
```java 
WeakReference.get() != null 
``` 

* Mark class or interface with ```@WeakWrap``` annotation and annotation processor will generate boilderplate code for you.
* Tiny wrapper implements/extends interface/class and delegates the call to the original interface/class and returns the result.
* If ```WeakReference is null``` skips the call and returns default value. 
* Default values are ```0``` and ```false``` (boolean) for primitive types and ```null``` for reference types. 

__Example:__

```java
public interface AddBookContract {

    @WeakWrap
    interface View {
        void showBook(Book book);
        void noBooksFound();
        void clearScreen();
        void bookAdded();
        void showError(Throwable error);
        void searchBookCanceled();

    }
    interface UserActionsListener {
        void scanBarcode();
        void searchBook(String isbn);
        void addBook();
        void clearScreen();
        void cancelSearchBook();
    }
}
```

__Will generate:__

```java
public class WeakWrapAddBookContractView implements AddBookContract.View{
  private final WeakReference<AddBookContract.View> weakWrap;

  public WeakWrapAddBookContractView(AddBookContract.View addBookContractView) {
    weakWrap = new WeakReference<>(addBookContractView);
  }

  public void showBook(Book book) {
    AddBookContract.View original = weakWrap.get();
    if(original != null) {
       original.showBook(book);
    }
  }

  public void noBooksFound() {
    AddBookContract.View original = weakWrap.get();
    if(original != null) {
       original.noBooksFound();
    }
  }
  ...
```

Usage
--------

Android:
-------

build.gradle in project root

```groovy

allprojects {
    repositories {
        jcenter()
        //add jitpack.io
        maven { url "https://jitpack.io" }
    }
}

buildscript {
  dependencies {
    classpath 'com.neenbedankt.gradle.plugins:android-apt:1.8'
  }
}
```

build.gradle in /app folder

```groovy
android{
    packagingOptions {
        exclude 'META-INF/services/javax.annotation.processing.Processor'
    }
}

apply plugin: 'com.neenbedankt.android-apt'

dependencies {
  compile 'com.github.stefandekanski:weakwrap:master-SNAPSHOT'
}
```




