Android Link Preview (written in Kotlin)
=========================

Preview from an url, grabbing all the information such as title, relevant text and image.

Based on
-----------------
https://github.com/LeonardoCardoso/Android-Link-Preview


What was changed
-----------------
Synchronously API method added


Usage example (Reactive)
-----------------
```java
Single
   .fromCallable { LinkPreloader.load("https://www.google.com/") }
   .subscribeOn(Schedulers.io())
   .observeOn(AndroidSchedulers.mainThread())
   .subscribe(
       { preview ->
           // TODO handle result here
       },
       { throwable ->
           // TODO handle error here
       }
   )
```

License
--------

    Copyright 2019 Siarhei Sinelnikau

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.