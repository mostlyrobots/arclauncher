import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/settings/back_button_actions.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class ButtonActionsPage extends StatelessWidget {
  static const String routeName = "button_actions_panel";

  const ButtonActionsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final isFireTv = context.select<SettingsService, bool>((s) => s.isFireTv);

    return Column(
      children: [
        SettingsPageHeader(title: localizations.buttonActions),
        Expanded(
          child: ListView(
            children: [
              FocusableSettingsTile(
                showChevron: true,
                autofocus: true,
                leading: const Icon(Icons.arrow_back),
                title: Text(localizations.backButtonAction, style: Theme.of(context).textTheme.bodyMedium),
                onPressed: () => Navigator.of(context).pushNamed(BackButtonActionPage.routeName),
              ),
              if (isFireTv)
                FocusableSettingsTile(
                  showChevron: true,
                  leading: const Icon(Icons.home_outlined),
                  title: Text(localizations.homeButtonAction, style: Theme.of(context).textTheme.bodyMedium),
                  onPressed: () => Navigator.of(context).pushNamed(HomeButtonActionPage.routeName),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class BackButtonActionPage extends StatelessWidget {
  static const String routeName = "back_button_action_panel";

  const BackButtonActionPage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;

    return Consumer<SettingsService>(
      builder: (context, service, _) => Column(
        children: [
          SettingsPageHeader(title: localizations.backButtonAction),
          Expanded(
            child: ListView(
              children: [
                _radioTile(context, localizations.dialogOptionBackButtonActionDoNothing,
                    service.backButtonAction == "", () => service.setBackButtonAction("")),
                _radioTile(context, localizations.dialogOptionBackButtonActionShowClock,
                    service.backButtonAction == BACK_BUTTON_ACTION_CLOCK,
                    () => service.setBackButtonAction(BACK_BUTTON_ACTION_CLOCK)),
                _radioTile(context, localizations.dialogOptionBackButtonActionShowScreensaver,
                    service.backButtonAction == BACK_BUTTON_ACTION_SCREENSAVER,
                    () => service.setBackButtonAction(BACK_BUTTON_ACTION_SCREENSAVER)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class HomeButtonActionPage extends StatelessWidget {
  static const String routeName = "home_button_action_panel";

  const HomeButtonActionPage({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;

    return Consumer<SettingsService>(
      builder: (context, service, _) => Column(
        children: [
          SettingsPageHeader(title: localizations.homeButtonAction),
          Expanded(
            child: ListView(
              children: [
                _radioTile(context, localizations.homeButtonActionDefault,
                    !service.fireTvHomeRemapEnabled, () => service.setFireTvHomeRemapEnabled(false)),
                _radioTile(context, localizations.homeButtonActionLaunchArc,
                    service.fireTvHomeRemapEnabled, () => service.setFireTvHomeRemapEnabled(true)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

Widget _radioTile(BuildContext context, String label, bool isSelected, VoidCallback onPressed) {
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
