import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    double width = MediaQuery.of(context).size.width;
    double height = MediaQuery.of(context).size.height;

    return Scaffold(
      body: Column(
        children: [
          Stack(
            children: [
              backgroundSection(),
              logoSection(),
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                introText(''),
                descriptionText(''),
                googleLoginButton(context),
                appleLoginButton(context),
                githubLoginButton(context),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget backgroundSection() {
    const double height = 275;
    return Container(
      constraints: const BoxConstraints.tightFor(height: height),
      child: SvgPicture.asset(
        'assets/smartphone_tilted.svg',
        fit: BoxFit.cover,
        height: height,
      ),
    );
  }

  Widget logoSection() {
    return SvgPicture.asset('assets/app_logo_no_bg.svg',
        height: 146, width: 177);
  }

  Widget introText(String text) {
    return Text(text, style: titleLarge);
  }

  Widget descriptionText(String text) {
    return Text(text, style: bodyMedium);
  }

  Widget googleLoginButton(BuildContext context) {
    return InkWell(
      onTap: null,
      child: Container(
        padding: const EdgeInsets.symmetric(
          vertical: 8.5,
        ),
        decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: lightColorScheme.outline,
            )),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            SvgPicture.asset(
              'assets/google_logo.svg',
              width: 24,
              height: 24,
            ),
            const Text(
              'Continue with Google',
              style: bodyMedium,
            )
          ],
        ),
      ),
    );
  }

  Widget appleLoginButton(BuildContext context) {
    return InkWell(
      onTap: null,
      child: Container(
        padding: const EdgeInsets.symmetric(
          vertical: 8.5,
        ),
        decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: lightColorScheme.outline,
            )),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            SvgPicture.asset(
              'assets/apple_logo.svg',
              width: 24,
              height: 24,
            ),
            const Text(
              'Continue with Apple',
              style: bodyMedium,
            )
          ],
        ),
      ),
    );
  }

  Widget githubLoginButton(BuildContext context) {
    return InkWell(
      onTap: null,
      child: Container(
        padding: const EdgeInsets.symmetric(
          vertical: 8.5,
        ),
        decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: lightColorScheme.outline,
            )),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            SvgPicture.asset(
              'assets/github_logo.svg',
              width: 24,
              height: 24,
            ),
            const Text(
              'Continue with GitHub',
              style: bodyMedium,
            )
          ],
        ),
      ),
    );
  }
}
