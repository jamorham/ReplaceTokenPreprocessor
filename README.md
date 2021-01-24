# android.replace.token.preprocessor
[![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg?style=flat-square)](https://opensource.org/licenses/MIT)

A simple replace token preprocessor gradle plugin for Android

# How to use

```

apply plugin: 'com.android.application'
apply plugin: 'com.github.jamorham.android.replace.token.preprocessor'

replaceAndroidTokenPreprocessorSettings {

    // Adjust package names example
    replace 'com.eveningoutpost.dexdrip.Services': "com.eveningoutpost.dexdrip.services"
    replace 'com.eveningoutpost.dexdrip.UtilityModels': "com.eveningoutpost.dexdrip.utilitymodels"
 
}
```
