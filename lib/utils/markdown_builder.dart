import 'package:flutter/material.dart';
import 'package:flutter_highlighter/flutter_highlighter.dart';
import 'package:flutter_highlighter/themes/monokai-sublime.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:markdown/markdown.dart' as md;
import 'package:google_fonts/google_fonts.dart';

class CodeElementBuilder extends MarkdownElementBuilder {
  @override
  Widget? visitElementAfter(md.Element element, TextStyle? preferredStyle) {
    var language = '';

    if (element.attributes['class'] != null) {
      String lg = element.attributes['class'] as String;
      language = lg.substring(9);
    }
    WidgetsFlutterBinding.ensureInitialized();
    print(element.textContent);
    return SizedBox(
      child: HighlightView(
        // The original code to be highlighted
        element.textContent,

        // Specify language
        // It is recommended to give it a value for performance
        language: language,

        // Specify highlight theme
        // All available themes are listed in `themes` folder
        theme: monokaiSublimeTheme,

        // Specify padding
        padding: const EdgeInsets.all(4),

        // Specify text style
        textStyle: GoogleFonts.jetBrainsMono(fontSize: 12),
      ),
    );
  }
}

class CodeMarkdownElementBuilder extends MarkdownElementBuilder {
  ScrollController verticalController = ScrollController();
  ScrollController horizontalController = ScrollController();
  @override
  Widget visitText(md.Text text, TextStyle? preferredStyle) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 200),
      child: Scrollbar(
        controller: verticalController,
        child: SingleChildScrollView(
          scrollDirection: Axis.vertical,
          controller: verticalController,
          child: Scrollbar(
            controller: horizontalController,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              controller: horizontalController,
              child: Text.rich(
                TextSpan(
                  text: '${text.text}--------------------------hi',
                  style: GoogleFonts.jetBrainsMono(),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
