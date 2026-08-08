import SwiftUI

public struct RemindersView: View {
    @State private var reminders: [ReminderItem] = [
        ReminderItem(id: "1", title: "Listen to music together", timeString: "8:00 PM", isCompleted: false, category: "Shared"),
        ReminderItem(id: "2", title: "Goodnight call", timeString: "11:00 PM", isCompleted: true, category: "Call")
    ]
    
    public var body: some View {
        ZStack {
            Color(UIColor.systemGroupedBackground).ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 16) {
                Text("Reminders")
                    .font(.system(size: 32, weight: .bold))
                    .padding(.horizontal)
                    .padding(.top, 60)
                
                List {
                    ForEach(reminders) { reminder in
                        HStack {
                            Image(systemName: reminder.isCompleted ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(reminder.isCompleted ? .purple : .gray)
                                .font(.system(size: 22))
                            
                            VStack(alignment: .leading) {
                                Text(reminder.title)
                                    .font(.system(size: 16, weight: .medium))
                                    .strikethrough(reminder.isCompleted)
                                Text(reminder.timeString)
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                    }
                }
                .listStyle(InsetGroupedListStyle())
            }
            .padding(.bottom, 90)
        }
    }
}
