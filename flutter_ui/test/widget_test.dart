import 'package:flutter_test/flutter_test.dart';
import 'package:alkamel_flutter_ui/main.dart';

void main() {
  testWidgets('dashboard renders Arabic title and quick actions', (tester) async {
    await tester.pumpWidget(const AlKamelFlutterApp());
    expect(find.text('الكامل أونلاين'), findsOneWidget);
    expect(find.text('الوصول السريع'), findsOneWidget);
    expect(find.text('إضافة كروت'), findsOneWidget);
    expect(find.text('المخزون الحالي'), findsOneWidget);
  });
}
