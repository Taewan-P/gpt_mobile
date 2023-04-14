import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:gpt_mobile/screens/platform_setup.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    double width = MediaQuery.of(context).size.width;
    double height = MediaQuery.of(context).size.height;
    // Print width and height
    print('Width: $width, Height: $height');

    return Scaffold(
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            Stack(
              // Vertical align
              alignment: Alignment.topCenter,
              children: [
                backgroundSection(height),
                logoSection(width, height),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  introText(
                      'The best AI assistant\nyou can get in your \nSmartphone.'),
                  const SizedBox(
                    height: 8,
                  ),
                  descriptionText(
                      'Use the model that suits you\nthe most. Ask anything you want!'),
                ],
              ),
            ),
            nextButton(context),
          ],
        ),
      ),
    );
  }

  Widget backgroundSection(double height) {
    double calc = height * 0.4;
    return Container(
        constraints: BoxConstraints(maxHeight: calc),
        // padding: const EdgeInsets.only(),
        margin: const EdgeInsets.only(
          top: 110,
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            Positioned(
              left: -60,
              child: SvgPicture.asset('assets/smartphone_tilted.svg',
                  fit: BoxFit.fitHeight, height: calc),
            )
          ],
        ));
  }

  Widget logoSection(double width, double height) {
    return Container(
      padding: EdgeInsets.only(top: height * 0.06),
      child: SvgPicture.asset(
        'assets/app_logo_no_bg.svg',
        width: width * 0.5,
      ),
    );
  }

  Widget introText(String text) {
    return Text(text, style: headlineLarge);
  }

  Widget descriptionText(String text) {
    return Text(text, style: bodyLarge, textAlign: TextAlign.left);
  }

  Widget nextButton(context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Align(
        alignment: Alignment.bottomRight,
        child: ElevatedButton(
          onPressed: () {
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => const PlatformSetup(),
              ),
            );
          },
          style: ElevatedButton.styleFrom(
              backgroundColor: lightColorScheme.primary,
              foregroundColor: lightColorScheme.onPrimary,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(99)),
              minimumSize: const Size(80, 80)),
          child: const Icon(
            Icons.arrow_forward_rounded,
            size: 32,
          ),
        ),
      ),
    );
  }
}
