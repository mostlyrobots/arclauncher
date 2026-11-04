/*
 * FLauncher
 * Copyright (C) 2024 Oscar Rojas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/widgets/rounded_switch_list_tile.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flauncher/widgets/settings/weather_city_dialog.dart';
import 'package:flauncher/widgets/weather_geocoding.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../providers/settings_service.dart';

class StatusBarPanelPage extends StatelessWidget {
  static const String routeName = "status_bar_panel";

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final settingsService = Provider.of<SettingsService>(context);

    return Column(
      children: [
        SettingsPageHeader(title: localizations.statusBar),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              RoundedSwitchListTile(
                autofocus: true,
                value: settingsService.autoHideAppBarEnabled,
                onChanged: (value) => settingsService.setAutoHideAppBarEnabled(value),
                title: Text(localizations.autoHideAppBar, style: Theme.of(context).textTheme.bodyMedium),
                secondary: const Icon(Icons.visibility_off_outlined),
              ),
              RoundedSwitchListTile(
                value: settingsService.hideStatusBarInGrid,
                onChanged: (value) => settingsService.setHideStatusBarInGrid(value),
                title: Text(localizations.hideStatusBarInGrid, style: Theme.of(context).textTheme.bodyMedium),
                secondary: const Icon(Icons.grid_view_outlined),
              ),
              const SettingsSectionDivider(),
              RoundedSwitchListTile(
                value: settingsService.showDateInStatusBar,
                onChanged: (value) => settingsService.setShowDateInStatusBar(value),
                title: Text(localizations.date),
                secondary: const Icon(Icons.calendar_today_outlined),
              ),
              RoundedSwitchListTile(
                value: settingsService.showTimeInStatusBar,
                onChanged: (value) => settingsService.setShowTimeInStatusBar(value),
                title: Text(localizations.time),
                secondary: const Icon(Icons.watch_later_outlined),
              ),
              RoundedSwitchListTile(
                value: settingsService.showWifiWidgetInStatusBar,
                onChanged: (value) => settingsService.setShowWifiWidgetInStatusBar(value),
                title: Text(localizations.wifiUsage),
                secondary: const Icon(Icons.wifi),
              ),
              RoundedSwitchListTile(
                value: settingsService.showNetworkIndicatorInStatusBar,
                onChanged: (value) => settingsService.setShowNetworkIndicatorInStatusBar(value),
                title: Text(localizations.networkIndicator),
                secondary: const Icon(Icons.signal_wifi_4_bar),
              ),
              FocusableSettingsTile(
                showChevron: true,
                leading: const Icon(Icons.wb_sunny_outlined),
                title: Text(localizations.weather),
                onPressed: () => Navigator.of(context).pushNamed(WeatherSettingsPage.routeName),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class WeatherSettingsPage extends StatelessWidget {
  static const String routeName = "weather_settings_panel";

  const WeatherSettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final settingsService = Provider.of<SettingsService>(context);

    return Column(
      children: [
        SettingsPageHeader(title: localizations.weather),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              RoundedSwitchListTile(
                autofocus: true,
                value: settingsService.showWeatherInStatusBar,
                onChanged: (value) => settingsService.setShowWeatherInStatusBar(value),
                title: Text(localizations.showWeather),
                secondary: const Icon(Icons.wb_sunny_outlined),
              ),
              FocusableSettingsTile(
                leading: const Icon(Icons.location_city_outlined),
                title: Text(localizations.weatherLocation),
                trailing: Text(settingsService.weatherCity ?? '-'),
                onPressed: () async {
                  final city = await showDialog<WeatherCity>(
                    context: context,
                    builder: (_) => const WeatherCityDialog(),
                  );
                  if (city != null) {
                    await settingsService.setWeatherLocation(city.name, city.latitude, city.longitude);
                  }
                },
              ),
              RoundedSwitchListTile(
                value: settingsService.showWeatherLocationInStatusBar,
                onChanged: (value) => settingsService.setShowWeatherLocationInStatusBar(value),
                title: Text(localizations.showWeatherLocation),
                secondary: const Icon(Icons.place_outlined),
              ),
              FocusableSettingsTile(
                showChevron: true,
                leading: const Icon(Icons.thermostat_outlined),
                title: Text(localizations.weatherUnits),
                onPressed: () => Navigator.of(context).pushNamed(WeatherUnitsPage.routeName),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class WeatherUnitsPage extends StatelessWidget {
  static const String routeName = "weather_units_panel";

  const WeatherUnitsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final service = Provider.of<SettingsService>(context);

    return Column(
      children: [
        SettingsPageHeader(title: localizations.weatherUnits),
        Expanded(
          child: ListView(
            children: [
              _unitTile(context, localizations.weatherUnitsImperial, service.weatherImperialUnits,
                  () => service.setWeatherImperialUnits(true)),
              _unitTile(context, localizations.weatherUnitsMetric, !service.weatherImperialUnits,
                  () => service.setWeatherImperialUnits(false)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _unitTile(BuildContext context, String label, bool isSelected, VoidCallback onPressed) {
    return FocusableSettingsTile(
      frameLeading: false,
      leading: Icon(
        isSelected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
        color: isSelected ? Theme.of(context).colorScheme.secondary : Colors.grey,
      ),
      title: Text(label, style: Theme.of(context).textTheme.bodyMedium),
      onPressed: onPressed,
    );
  }
}
