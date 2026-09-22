import FirebaseFirestore

struct GroupMember: Codable {
    enum Role: String, Codable {
        case owner, member
    }

    var displayName: String
    var photoURL: String?
    var joinedAt: Timestamp
    var role: Role
}
