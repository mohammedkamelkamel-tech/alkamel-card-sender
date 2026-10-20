import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const Color skyBlue = Color(0xFF77C9F5);
const Color paleBlue = Color(0xFFF0F9FF);
const Color deepBlue = Color(0xFF145477);
const Color gold = Color(0xFFE9C78F);
const Color ink = Color(0xFF173B55);
const MethodChannel _nativeNavigation =
    MethodChannel('ye.alkamel.cardsender/navigation');

Future<void> _openNative(String destination) async {
  try {
    await _nativeNavigation.invokeMethod<void>('open', {'destination': destination});
  } on PlatformException {
    // Native bridge is added during the integration step; keep the UI preview usable.
  } on MissingPluginException {
    // Allows running this design as a standalone Flutter preview.
  }
}

void main() => runApp(const AlKamelFlutterApp());

class AlKamelFlutterApp extends StatelessWidget {
  const AlKamelFlutterApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'الكامل أونلاين',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: paleBlue,
        colorScheme: ColorScheme.fromSeed(seedColor: skyBlue, primary: deepBlue),
        fontFamily: 'sans-serif',
      ),
      home: const DashboardPage(),
    );
  }
}

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  int _selectedTab = 0;

  static const _items = <_DashboardItem>[
    _DashboardItem(Icons.add_card_rounded, 'إضافة كروت', 'إضافة وإدارة الكروت', 'addCards'),
    _DashboardItem(Icons.link_rounded, 'ربط الأرقام', 'إدارة الأرقام البديلة', 'alternateNumbers'),
    _DashboardItem(Icons.inventory_2_rounded, 'المخزون الحالي', 'متابعة الكروت المتوفرة', 'stock'),
    _DashboardItem(Icons.point_of_sale_rounded, 'المبيعات والأرباح', 'ملخص المبيعات والأرباح', 'sales'),
    _DashboardItem(Icons.receipt_long_rounded, 'سجل العمليات', 'عرض العمليات السابقة', 'operations'),
    _DashboardItem(Icons.sim_card_rounded, 'مخزون الشرائح', 'إدارة الشرائح والأرقام', 'simInventory'),
    _DashboardItem(Icons.send_rounded, 'خدمات الإرسال', 'إدارة الإرسال والطابور', 'services'),
    _DashboardItem(Icons.router_rounded, 'إدارة MikroTik', 'الاتصال ومتابعة الشبكة', 'mikrotik'),
    _DashboardItem(Icons.backup_rounded, 'النسخ الاحتياطي', 'حفظ واستعادة البيانات', 'backup'),
    _DashboardItem(Icons.notifications_active_rounded, 'تنبيهات المخزون', 'إعداد تنبيهات النقص', 'stockAlerts'),
  ];

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        body: SafeArea(
          bottom: false,
          child: Column(
            children: [
              _buildHeader(),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 18),
                  children: [
                    _featuredCard(),
                    const SizedBox(height: 18),
                    const Padding(
                      padding: EdgeInsets.only(bottom: 12, right: 2),
                      child: Text(
                        'الوصول السريع',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: ink),
                      ),
                    ),
                    GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: _items.length,
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 2,
                        mainAxisSpacing: 12,
                        crossAxisSpacing: 12,
                        mainAxisExtent: 154,
                      ),
                      itemBuilder: (context, index) => _ActionCard(
                        item: _items[index],
                        onTap: () => _openNative(_items[index].destination),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        bottomNavigationBar: _buildBottomNavigation(),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topRight,
          end: Alignment.bottomLeft,
          colors: [skyBlue, Color(0xFFBDE8FF)],
        ),
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(28)),
      ),
      child: Row(
        children: [
          Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.48),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: Colors.white.withValues(alpha: 0.8)),
            ),
            child: const Icon(Icons.wifi_tethering_rounded, color: deepBlue, size: 34),
          ),
          const SizedBox(width: 14),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('الكامل أونلاين',
                    style: TextStyle(color: ink, fontSize: 25, fontWeight: FontWeight.w900)),
                SizedBox(height: 3),
                Text('إدارة الكروت والمبيعات والإرسال التلقائي',
                    style: TextStyle(color: deepBlue, fontSize: 12.5, fontWeight: FontWeight.w600)),
              ],
            ),
          ),
          IconButton(
            tooltip: 'المزيد',
            onPressed: () => _openNative('more'),
            icon: const Icon(Icons.more_vert_rounded, color: ink, size: 28),
          ),
        ],
      ),
    );
  }

  Widget _featuredCard() {
    return Material(
      color: Colors.white,
      elevation: 2,
      shadowColor: deepBlue.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        borderRadius: BorderRadius.circular(22),
        onTap: () => _openNative('services'),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF5E3),
                  borderRadius: BorderRadius.circular(17),
                ),
                child: const Icon(Icons.settings_suggest_rounded, color: gold, size: 34),
              ),
              const SizedBox(width: 14),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('إدارة الخدمات والمراقبة',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: ink)),
                    SizedBox(height: 5),
                    Text('إدارة الشمول والمحفظة وطابور الإرسال',
                        style: TextStyle(fontSize: 12, color: Color(0xFF647F91))),
                  ],
                ),
              ),
              const Icon(Icons.chevron_left_rounded, color: deepBlue),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBottomNavigation() {
    const tabs = [
      (Icons.home_rounded, 'الرئيسية', 'home'),
      (Icons.inventory_2_outlined, 'المخزون', 'stock'),
      (Icons.send_rounded, 'الإرسال', 'services'),
      (Icons.bar_chart_rounded, 'التقارير', 'sales'),
    ];
    return NavigationBar(
      selectedIndex: _selectedTab,
      backgroundColor: Colors.white,
      indicatorColor: skyBlue.withValues(alpha: 0.30),
      elevation: 10,
      onDestinationSelected: (index) {
        setState(() => _selectedTab = index);
        _openNative(tabs[index].$3);
      },
      destinations: [
        for (final tab in tabs)
          NavigationDestination(icon: Icon(tab.$1), label: tab.$2),
      ],
    );
  }
}

class _DashboardItem {
  final IconData icon;
  final String title;
  final String subtitle;
  final String destination;

  const _DashboardItem(this.icon, this.title, this.subtitle, this.destination);
}

class _ActionCard extends StatelessWidget {
  final _DashboardItem item;
  final VoidCallback onTap;

  const _ActionCard({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      elevation: 2.5,
      shadowColor: deepBlue.withValues(alpha: 0.10),
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 13),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 54,
                height: 54,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFFFFF8EB), Color(0xFFFFE9C2)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Icon(item.icon, size: 30, color: const Color(0xFFB9853F)),
              ),
              const SizedBox(height: 10),
              Text(item.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: ink)),
              const SizedBox(height: 4),
              Text(item.subtitle,
                  maxLines: 2,
                  textAlign: TextAlign.center,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 10.5, height: 1.3, color: Color(0xFF6C8798))),
            ],
          ),
        ),
      ),
    );
  }
}
