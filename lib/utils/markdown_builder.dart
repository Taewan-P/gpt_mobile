import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

// class Pre extends StatelessWidget {
//   final String text;

//   const Pre({Key? key, required this.text}) : super(key: key);

//   @override
//   Widget build(BuildContext context) {
//     return Column(
//       children: [
//         Stack(
//           fit: StackFit.loose,
//           alignment: Alignment.topRight,
//           children: [
//             Container(
//               width: double.infinity,
//               padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
//               decoration: BoxDecoration(
//                 color: Colors.black,
//                 borderRadius: BorderRadius.circular(4),
//               ),
//               // child: HighlightView(
//               //   text,
//               //   language: '',
//               //   theme: monokaiSublimeTheme,
//               //   padding: const EdgeInsets.all(4),
//               //   textStyle: GoogleFonts.jetBrainsMono(fontSize: 12),
//               // )

//               child: Padding(
//                 padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
//                 child: SelectableText(
//                   text,
//                   style: Theme.of(context)
//                       .textTheme
//                       .bodySmall
//                       ?.copyWith(color: Colors.white),
//                 ),
//               ),
//             ),
//             Container(
//               child: IconButton(
//                 onPressed: () {
//                   final data = ClipboardData(text: text);
//                   Clipboard.setData(data);
//                 },
//                 tooltip: 'Copy to clipboard',
//                 icon: const Icon(
//                   Icons.content_copy_outlined,
//                   size: 20,
//                   color: Colors.white,
//                 ),
//               ),
//             ),
//           ],
//         ),
//       ],
//     );
//   }
// }

// class CodeElementBuilder extends MarkdownElementBuilder {
//   @override
//   Widget? visitElementAfter(md.Element element, TextStyle? preferredStyle) {
//     var language = '';

//     if (element.attributes['class'] != null) {
//       String lg = element.attributes['class'] as String;
//       language = lg.substring(9);
//     }
//     WidgetsFlutterBinding.ensureInitialized();
//     print(element.textContent);
//     return SizedBox(
//       child: HighlightView(
//         // The original code to be highlighted
//         element.textContent,

//         // Specify language
//         // It is recommended to give it a value for performance
//         language: language,

//         // Specify highlight theme
//         // All available themes are listed in `themes` folder
//         theme: monokaiSublimeTheme,

//         // Specify padding
//         padding: const EdgeInsets.all(4),

//         // Specify text style
//         textStyle: GoogleFonts.jetBrainsMono(fontSize: 12),
//       ),
//     );
//   }
// }

// class CodeMarkdownElementBuilder extends MarkdownElementBuilder {
//   String? language;

//   @override
//   void visitElementBefore(md.Element element) {
//     for (var n in element.children ?? []) {
//       print(n.tag);
//     }
//     if (element.attributes['class'] != null) {
//       String lg = element.attributes['class'] as String;
//       language = lg.substring(9);
//       print("hi");
//     }
//     super.visitElementBefore(element);
//   }

//   // @override
//   // Widget visitText(md.Text text, TextStyle? preferredStyle) {
//   //   // if (element.tag == 'pre') {
//   //   //   preferredStyle ??= const TextStyle();
//   //   // }
//   //   print("language: $language");
//   //   return Pre(text: text.text);
//   // }

//   // @override
//   // Widget? visitElementAfter(md.Element element, TextStyle? preferredStyle) {
//   //   return Container(
//   //     padding: const EdgeInsets.all(8),
//   //     decoration: BoxDecoration(
//   //       borderRadius: BorderRadius.circular(4),
//   //       color: Colors.grey.shade200,
//   //     ),
//   //     child: Text(
//   //       element.textContent,
//   //       style: const TextStyle(fontFamily: 'Roberto', color: Colors.deepOrange),
//   //     ),
//   //   );
//   // }
//   @override
//   Widget visitText(md.Text text, TextStyle? preferredStyle) {
//     ScrollController verticalController = ScrollController();
//     ScrollController horizontalController = ScrollController();
//     return ConstrainedBox(
//       constraints: const BoxConstraints(maxHeight: 200),
//       child: Scrollbar(
//         controller: verticalController,
//         child: SingleChildScrollView(
//           scrollDirection: Axis.vertical,
//           controller: verticalController,
//           child: Scrollbar(
//             controller: horizontalController,
//             child: SingleChildScrollView(
//               scrollDirection: Axis.horizontal,
//               controller: horizontalController,
//               child: Text.rich(
//                 TextSpan(
//                   text: '${text.text}--------------------------hi',
//                   style: GoogleFonts.jetBrainsMono(),
//                 ),
//               ),
//             ),
//           ),
//         ),
//       ),
//     );
//   }
// }

class CodeWrapperWidget extends StatefulWidget {
  final Widget child;
  final String text;

  const CodeWrapperWidget({Key? key, required this.child, required this.text})
      : super(key: key);

  @override
  State<CodeWrapperWidget> createState() => _PreWrapperState();
}

class _PreWrapperState extends State<CodeWrapperWidget> {
  late Widget _switchWidget;
  bool hasCopied = false;

  @override
  void initState() {
    super.initState();
    _switchWidget = Icon(Icons.copy_rounded, key: UniqueKey());
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        SizedBox(
          width: 340,
          child: widget.child,
        ),
        Align(
          alignment: Alignment.topRight,
          child: Container(
            padding: const EdgeInsets.all(8.0),
            child: IconButton(
              onPressed: () {
                final data = ClipboardData(text: widget.text);
                Clipboard.setData(data);
              },
              tooltip: 'Copy to clipboard',
              icon: const Icon(
                Icons.content_copy_outlined,
                size: 20,
                color: Colors.white,
              ),
            ),
          ),
        )
      ],
    );
  }

  void refresh() {
    if (mounted) setState(() {});
  }
}
