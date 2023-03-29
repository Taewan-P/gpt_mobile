import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:google_sign_in/google_sign_in.dart';
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
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Stack(
              // Vertical align
              alignment: Alignment.topCenter,
              children: [
                backgroundSection(height),
                logoSection(width),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  introText(
                      'The best AI assistant\nyou can get in your \nSmartphone.'),
                  const SizedBox(
                    height: 8,
                  ),
                  descriptionText(
                      'Use the model that suits you\nthe most. Ask anything you want!'),
                  const SizedBox(
                    height: 24,
                  ),
                  Wrap(
                    children: [
                      googleLoginButton(context),
                      appleLoginButton(context),
                      githubLoginButton(context),
                    ],
                  ),
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
              left: -60,
              child: SvgPicture.asset('assets/smartphone_tilted.svg',
                  fit: BoxFit.fitHeight, height: calc),
            )
          ],
        ));
  }

  Widget logoSection(double width) {
    return Container(
      padding: const EdgeInsets.only(top: 50),
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

  Widget googleLoginButton(BuildContext context) {
    return OutlinedButton(
      onPressed: () {
        signInWithGoogle().then((result) {
          print('User logged in successfully');
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: ((context) => const PlatformSetup()),
            ),
          );
        }).catchError((e) {
          print(e);
        });
      },
      style: OutlinedButton.styleFrom(
        foregroundColor: lightColorScheme.primary,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
      ),
      child: Row(
        children: [
          SvgPicture.asset(
            'assets/google_logo.svg',
            width: 24,
            height: 24,
          ),
          Expanded(
            child: Text(
              'Continue with Google',
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.black,
              ).merge(bodyMedium),
            ),
          )
        ],
      ),
    );
  }

  Widget appleLoginButton(BuildContext context) {
    return OutlinedButton(
      onPressed: () {},
      style: OutlinedButton.styleFrom(
        foregroundColor: lightColorScheme.primary,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
      ),
      child: Row(
        children: [
          SvgPicture.asset(
            'assets/apple_logo.svg',
            width: 24,
            height: 24,
          ),
          Expanded(
            child: Text(
              'Continue with Apple',
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.black,
              ).merge(bodyMedium),
            ),
          ),
        ],
      ),
    );
  }

  Widget githubLoginButton(BuildContext context) {
    return OutlinedButton(
      onPressed: () {},
      style: OutlinedButton.styleFrom(
        foregroundColor: lightColorScheme.primary,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
      ),
      child: Row(
        children: [
          SvgPicture.asset(
            'assets/github_logo.svg',
            width: 24,
            height: 24,
          ),
          Expanded(
            child: Text(
              'Continue with GitHub',
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.black,
              ).merge(bodyMedium),
            ),
          )
        ],
      ),
    );
  }

  Future<UserCredential> signInWithGoogle() async {
    // Trigger the authentication flow
    final GoogleSignInAccount? googleUser = await GoogleSignIn().signIn();

    // Obtain the auth details from the request
    final GoogleSignInAuthentication? googleAuth =
        await googleUser?.authentication;

    // Create a new credential
    final credential = GoogleAuthProvider.credential(
      accessToken: googleAuth?.accessToken,
      idToken: googleAuth?.idToken,
    );

    // Once signed in, return the UserCredential
    return await FirebaseAuth.instance.signInWithCredential(credential);
  }
}
