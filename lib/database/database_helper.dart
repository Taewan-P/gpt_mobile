import 'dart:async';

import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

class Conversation {
  final int id;
  final int createdAt;

  String title;
  int updatedAt;

  Conversation(
      {required this.id,
      required this.createdAt,
      required this.title,
      required this.updatedAt});

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'created_at': createdAt,
      'updated_at': updatedAt,
    };
  }

  @override
  String toString() {
    return 'Conversation{id: $id, title: $title, createdAt: $createdAt, updatedAt: $updatedAt}';
  }
}

class Message {
  final int id;
  final int conversationId;
  final int messageId;
  final String sender;
  final String provider;
  final int createdAt;

  String content;

  Message(
      {required this.id,
      required this.conversationId,
      required this.messageId,
      required this.sender,
      required this.provider,
      required this.createdAt,
      required this.content});

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
  static const _dbVersion =
      2; // Change this if you want to execute onCreate again
  late Future<Database> database = _getDatabase();

  Future<Database> _getDatabase() async {
    final database = await openDatabase(
      join(await getDatabasesPath(), _dbName),
      onCreate: (db, version) {
        db.execute(
          '''
        CREATE TABLE IF NOT EXISTS Conversations (
            id INTEGER PRIMARY KEY,
            title TEXT,
            created_at INTEGER,
            updated_at INTEGER
        )
        ''',
        );
        db.execute(
          '''
        CREATE TABLE IF NOT EXISTS Messages (
            id INTEGER PRIMARY KEY,
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
    return await db.insert(table, row,
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<List<Map<String, dynamic>>> queryAllConversations() async {
    Database db = await database;
    return await db.query('Conversations', orderBy: 'updated_at DESC');
  }

  Future<List<Map<String, dynamic>>> queryAllMessages(
      int conversationID) async {
    Database db = await database;
    return await db.query('Messages',
        where: 'conv_id = ?',
        whereArgs: [conversationID],
        orderBy: 'msg_id DESC');
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
}
