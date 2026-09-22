import FirebaseFirestore

enum Volume: String, Codable, CaseIterable {
    case low, medium, high
}

struct LogEntry: Codable {
    var uid: String
    var displayName: String
    var timestamp: Timestamp
    var durationSeconds: Int
    var volume: Volume
    var dateKeyLocal: String
    var weekKeyLocal: String
    // nil encodes as FieldValue.serverTimestamp() via @ServerTimestamp; set once by Firestore.
    @ServerTimestamp var createdAt: Timestamp?
}
