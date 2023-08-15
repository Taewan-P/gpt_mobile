import 'dart:async';

import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

class Conversation {
  final int id;
  final int createdAt;

  String title;
  int updatedAt;
  int selectedAPI;

  Conversation({
    required this.id,
    required this.createdAt,
    required this.title,
    required this.updatedAt,
    required this.selectedAPI,
  });

  // selectedAPI 설명

  // Unix Permission 이랑 같다고 생각하면 됨
  // 1 : OpenAI
  // 2 : Anthropic
  // 4 : Google
  // 8 : 추후에 추가될 API
  // 16 : 추후에 추가될 API2...

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'created_at': createdAt,
      'updated_at': updatedAt,
      'selected_api': selectedAPI,
    };
  }

  @override
  String toString() {
    return 'Conversation{id: $id, title: $title, createdAt: $createdAt, updatedAt: $updatedAt, selectedAPI: $selectedAPI}';
  }
}

class Message {
  final int conversationId;
  final int messageId;
  final String sender;
  final String provider;

  int id;
  int createdAt;
  String content;

  Message({
    required this.id,
    required this.conversationId,
    required this.messageId,
    required this.sender,
    required this.provider,
    required this.createdAt,
    required this.content,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'conv_id': conversationId,
      'msg_id': messageId,
      'sender': sender,
      'provider': provider,
      'content': content,
      'created_at': createdAt,
    };
  }

  @override
  String toString() {
    return 'Message{id: $id, conversationId: $conversationId, messageId: $messageId, sender: $sender, provider: $provider, createdAt: $createdAt, content: $content}';
  }
}

class DatabaseHelper {
  static const _dbName = "gptmobile.db";
  static const _dbVersion = 4; // Change this if you want to execute onCreate again
  static Database? _database;

  static Map<String, bool> toBinaryMap(int number) {
    String binaryString = number.toRadixString(2);

    Map<String, bool> binaryMap = {'openai': false, 'anthropic': false, 'google': false};

    for (int i = 0; i < binaryString.length; i++) {
      switch (i) {
        case 0:
          binaryMap['openai'] = binaryString[i] == '1';
          break;
        case 1:
          binaryMap['anthropic'] = binaryString[i] == '1';
          break;
        case 2:
          binaryMap['google'] = binaryString[i] == '1';
          break;
        default:
      }
    }

    return binaryMap;
  }

  static int toBinaryInt(Map<String, bool> apiMap) {
    var result = 0;

    for (int i = 0; i < apiMap.length; i++) {
      switch (apiMap.keys.elementAt(i)) {
        case 'openai':
          if (apiMap.values.elementAt(i)) {
            result += 1;
          }
          break;
        case 'anthropic':
          if (apiMap.values.elementAt(i)) {
            result += 2;
          }
          break;
        case 'google':
          if (apiMap.values.elementAt(i)) {
            result += 4;
          }
          break;
        default:
      }
    }

    return result;
  }

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _getDatabase();
    return _database!;
  }

  Future<Database> _getDatabase() async {
    final database = await openDatabase(
      join(await getDatabasesPath(), _dbName),
      onCreate: (db, version) {
        db.execute(
          '''
        CREATE TABLE IF NOT EXISTS Conversations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT,
            created_at INTEGER,
            updated_at INTEGER,
            selected_api INTEGER
        )
        ''',
        );
        db.execute(
          '''
        CREATE TABLE IF NOT EXISTS Messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            conv_id INTEGER,
            msg_id INTEGER,
            sender TEXT,
            content TEXT,
            provider TEXT,
            created_at INTEGER,
            FOREIGN KEY (conv_id) REFERENCES Conversation(id)
        )
        ''',
        );
      },
      version: _dbVersion,
    );
    return database;
  }

  Future<int> insert(String table, Map<String, dynamic> row) async {
    Database db = await database;
    Map<String, dynamic> insertingRow = {};

    for (String key in row.keys) {
      insertingRow[key] = row[key];
    }

    if (insertingRow.containsKey('id')) {
      insertingRow.remove('id');
    }
    return await db.insert(table, insertingRow, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<List<Map<String, dynamic>>> queryAllConversations() async {
    Database db = await database;

    return await db.query('Conversations', orderBy: 'updated_at DESC');
  }

  Future<List<Map<String, dynamic>>> queryAllMessages(int conversationID) async {
    Database db = await database;

    return await db.query('Messages', where: 'conv_id = ?', whereArgs: [conversationID], orderBy: 'msg_id ASC');
  }

  Future<int> update(String table, Map<String, dynamic> row) async {
    Database db = await database;
    int rowID = row['id'];

    return await db.update(table, row, where: 'id = ?', whereArgs: [rowID]);
  }

  Future<int> delete(String table, int id) async {
    Database db = await database;

    return await db.delete(table, where: 'id = ?', whereArgs: [id]);
  }

  void close() async {
    final db = await database;
    _database = null;
    return db.close();
  }
}
