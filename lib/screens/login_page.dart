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
    debugPrint('Width: $width, Height: $height');

    return Scaffold(
      floatingActionButton: FloatingActionButton(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => const PlatformSetup(),
          ),
        ),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)),
        backgroundColor: lightColorScheme.primary,
        foregroundColor: lightColorScheme.onPrimary,
        child: const Icon(
          Icons.arrow_forward_rounded,
          size: 32,
        ),
      ),
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
                  introText('The best AI assistant\nyou can get in your \nSmartphone.'),
                  const SizedBox(
                    height: 8,
                  ),
                  descriptionText('Use the model that suits you\nthe most. Ask anything you want!'),
                ],
              ),
            ),
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
              child: SvgPicture.asset('assets/smartphone_tilted.svg', fit: BoxFit.fitHeight, height: calc),
            )
          ],
        ));
  }

  Widget logoSection(double width, double height) {
    return Container(
      padding: EdgeInsets.only(top: height * 0.06),
      child: SvgPicture.asset(
        'assets/app_logo_no_bg.svg',
        width: width * 0.45,
      ),
    );
  }

  Widget introText(String text) {
    return Text(text, style: headlineLarge);
  }

  Widget descriptionText(String text) {
    return Text(text, style: bodyLarge, textAlign: TextAlign.left);
  }
}
