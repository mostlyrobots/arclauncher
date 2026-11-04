import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/settings/status_bar_panel_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';

import '../../mocks.mocks.dart';

void main() {
  group('StatusBarPanelPage & WeatherSettingsPage', () {
    late MockSettingsService settingsService;

    setUp(() {
      settingsService = MockSettingsService();
      when(settingsService.autoHideAppBarEnabled).thenReturn(false);
      when(settingsService.hideStatusBarInGrid).thenReturn(false);
      when(settingsService.showDateInStatusBar).thenReturn(false);
      when(settingsService.showTimeInStatusBar).thenReturn(true);
      when(settingsService.showWifiWidgetInStatusBar).thenReturn(false);
      when(settingsService.showNetworkIndicatorInStatusBar).thenReturn(true);
      when(settingsService.setAutoHideAppBarEnabled(any)).thenAnswer((_) async {});
      when(settingsService.setHideStatusBarInGrid(any)).thenAnswer((_) async {});
      when(settingsService.showWeatherInStatusBar).thenReturn(false);
      when(settingsService.showWeatherLocationInStatusBar).thenReturn(true);
      when(settingsService.weatherImperialUnits).thenReturn(false);
      when(settingsService.setShowWeatherInStatusBar(any)).thenAnswer((_) async {});
      when(settingsService.setShowWeatherLocationInStatusBar(any)).thenAnswer((_) async {});
      when(settingsService.setWeatherImperialUnits(any)).thenAnswer((_) async {});
    });

    Widget createTestWidget([Widget? child]) {
      return ChangeNotifierProvider<SettingsService>.value(
        value: settingsService,
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: child ?? StatusBarPanelPage()),
        ),
      );
    }

    testWidgets('renders hideStatusBarInGrid and Weather submenu tile', (tester) async {
      await tester.pumpWidget(createTestWidget());
      await tester.pumpAndSettle();

      expect(find.text('Hide status bar in grid'), findsOneWidget);
      expect(find.text('Automatically hide status bar'), findsOneWidget);
      expect(find.text('Weather'), findsOneWidget);
    });

    testWidgets('toggling hideStatusBarInGrid calls setHideStatusBarInGrid', (tester) async {
      await tester.pumpWidget(createTestWidget());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Hide status bar in grid'));
      await tester.pumpAndSettle();

      verify(settingsService.setHideStatusBarInGrid(true)).called(1);
    });

    testWidgets('WeatherSettingsPage toggles show weather and show location', (tester) async {
      await tester.pumpWidget(createTestWidget(const WeatherSettingsPage()));
      await tester.pumpAndSettle();

      expect(find.text('Weather location'), findsOneWidget);
      expect(find.text('Localization'), findsOneWidget);

      await tester.tap(find.text('Show weather'));
      await tester.pumpAndSettle();
      verify(settingsService.setShowWeatherInStatusBar(true)).called(1);

      await tester.tap(find.text('Show location'));
      await tester.pumpAndSettle();
      verify(settingsService.setShowWeatherLocationInStatusBar(false)).called(1);
    });

    testWidgets('WeatherUnitsPage switches between Imperial and Metric', (tester) async {
      await tester.pumpWidget(createTestWidget(const WeatherUnitsPage()));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Imperial'));
      await tester.pumpAndSettle();
      verify(settingsService.setWeatherImperialUnits(true)).called(1);
    });
  });
}
