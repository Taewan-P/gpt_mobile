import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class CodeWrapperWidget extends StatefulWidget {
  final Widget child;
  final String text;

  const CodeWrapperWidget({Key? key, required this.child, required this.text})
      : super(key: key);

  @override
  State<CodeWrapperWidget> createState() => _PreWrapperState();
}

class _PreWrapperState extends State<CodeWrapperWidget> {
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
