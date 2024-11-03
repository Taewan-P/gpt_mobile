<div align="center">

<img width="200" height="200" style="display: block;" src="./images/logo.png">

# GPT Mobile

### Chat Assistant for Android that supports chatting with multiple models at once.

<p>
  <a href="https://mailchi.mp/kotlinweekly/kotlin-weekly-431"><img alt="Kotlin Weekly" src="https://img.shields.io/badge/Kotlin%20Weekly-%23431-blue"/></a>
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-green.svg"/>
  <img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/Taewan-P/gpt_mobile/release-build.yml">
  <a href="https://github.com/Taewan-P/gpt_mobile/releases/"><img alt="GitHub Releases Total Downloads" src="https://img.shields.io/github/downloads/Taewan-P/gpt_mobile/total?label=Downloads&logo=github"/></a>
  <a href="https://github.com/Taewan-P/gpt_mobile/releases/latest/"><img alt="GitHub Releases (latest by date)" src="https://img.shields.io/github/v/release/Taewan-P/gpt_mobile?color=black&label=Stable&logo=github"/></a>
</p>


</div>


## Screenshots

<div align="center">

<img style="display: block;" src="./images/screenshots.png">

</div>

## Demos


| <video src="https://github.com/Taewan-P/gpt_mobile/assets/27392567/96229e6d-6795-48b4-a915-aca915bd2527"/> | <video src="https://github.com/Taewan-P/gpt_mobile/assets/27392567/1cc13413-7320-4f6f-ace9-de76de58adcc"/> | <video src="https://github.com/Taewan-P/gpt_mobile/assets/27392567/546e2694-953d-4d67-937f-a29fba81046f"/> |
|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|


## Features

- **Chat with multiple models at once**
  - Uses official APIs for each platforms
  - Supported platforms:
    - OpenAI GPT
    - Anthropic Claude
    - Google Gemini
    - Groq
    - Ollama
  - Can customize temperature, top p (Nucleus sampling), and system prompt
  - Custom API URLs, Custom Models are also supported
- Local chat history
  - Chat history is **only saved locally**
  - Only sends to official API servers while chatting
- [Material You](https://m3.material.io/) style UI, Icons
  - Supports dark mode, system dynamic theming **without Activity restart**
- Per app language setting for Android 13+
- 100% Kotlin, Jetpack Compose, Single Activity, [Modern App Architecture](https://developer.android.com/topic/architecture#modern-app-architecture) in Android developers documentation


## To be supported

- Manual Languages Setting for Android 12 and below
- More platforms
- Image, file support for multimodal models

If you have any feature requests, please open an issue.


## Downloads

You can download the app from the following sites:

[<img height="80" alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"/>](https://f-droid.org/packages/dev.chungjungsoo.gptmobile)
[<img height="80" alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/>](https://play.google.com/store/apps/details?id=dev.chungjungsoo.gptmobile&utm_source=github&utm_campaign=gh-readme)
[<img height="80" alt='Get it on GitHub' src='https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png'/>](https://github.com/Taewan-P/gpt_mobile/releases)

Cross platform updates are supported. However, GitHub Releases will be the fastest track among the platforms since there is no verification/auditing process. (Probably 1 week difference?)


## Build

1. Clone repo
2. Open in Android Studio
3. Click `Run` or do Gradle build


## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Taewan-P/gpt_mobile&type=Timeline)](https://star-history.com/#Taewan-P/gpt_mobile&Timeline)


## License

See [LICENSE](./LICENSE) for details.

[F-Droid Icon License](https://gitlab.com/fdroid/artwork/-/blob/master/fdroid-logo-2015/README.md)

