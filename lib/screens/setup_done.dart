import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/chat_list.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class SetupDone extends StatelessWidget {
  const SetupDone({super.key});

  @override
  Widget build(BuildContext context) {
    double width = MediaQuery.of(context).size.width;
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
          iconTheme: const IconThemeData(size: 28),
          backgroundColor: lightColorScheme.surface,
          surfaceTintColor: Colors.transparent),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              setDoneTitle(),
              const SizedBox(
                height: 24,
              ),
              setDoneDescription(),
              const Spacer(),
              doneIcon(width),
              const Spacer(),
              doneButton(context),
            ],
          ),
        ),
      ),
    );
  }

  Widget setDoneTitle() {
    return const Text(
      'You\'re all set!',
      style: displayMedium,
    );
  }

  Widget setDoneDescription() {
    return const Text(
      'Let\'s start using GPT Mobile!',
      style: bodyLarge,
    );
  }

  Widget doneIcon(double width) {
    final iconWidth = width * 0.7;
    return Center(
      child: Icon(
        Icons.check_circle_outline_rounded,
        color: lightColorScheme.primary,
        size: iconWidth,
      ),
    );
  }

  Widget doneButton(BuildContext context) {
    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        width: double.maxFinite,
        child: ElevatedButton(
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => const ChatList(),
            ),
          ),
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(
              vertical: 16,
            ),
            backgroundColor: lightColorScheme.primary,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
          child: const Text(
            'Done',
            style: titleMedium,
          ),
        ),
      ),
    );
  }
}
