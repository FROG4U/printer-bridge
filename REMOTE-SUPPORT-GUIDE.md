# Remote support for restaurant tablets (see & fix them from anywhere)

This lets you view and control a restaurant's Android tablet from your own phone
or laptop - so when they call about the printer or anything on the tablet, you can
sort it live without driving out. It's the thing the ResDineOS dashboard can't do,
because the Bluetooth/printer lives on the physical tablet.

**Recommended app: AnyDesk** (free to try; best-in-class Android support).
TeamViewer QuickSupport is a fine alternative if you ever prefer it.

---

## One-time setup ON EACH TABLET (do this once per restaurant, ~3 min)

1. Open the **Play Store**, search **AnyDesk**, install it.
2. Open AnyDesk. It shows a **9-digit address** (e.g. `123 456 789`) - **write it down
   next to the restaurant's name** (keep a little list: "Patiala = 123 456 789").
3. (For control, not just viewing) AnyDesk will offer to install a small **"AnyDesk
   control" plug-in / enable Accessibility** - allow it. Viewing works without it;
   *controlling* the screen (tapping buttons for them) needs it. Most tablets support it.
4. **Choose how you'll connect** - pick ONE:
   - **On-demand (more private):** do nothing extra. Each time you connect, someone at
     the restaurant taps **Accept** on the tablet. Good if staff are always there.
   - **Unattended (connect anytime - recommended for your own tablets):** in AnyDesk →
     **Settings → Security → Unattended Access → set a password.** Now you can connect
     whenever you like without anyone tapping Accept. Use a strong password and keep it safe.
5. Leave AnyDesk installed. It can sit in the background; no need to keep it open.

---

## One-time setup ON YOUR phone/laptop

1. Install **AnyDesk** on your phone or laptop (same app, from the store / anydesk.com).
2. That's it - you'll type the tablet's 9-digit address here to connect.

---

## When a restaurant calls with a problem

1. Open AnyDesk on your phone/laptop.
2. Type the **tablet's 9-digit address** (from your list) and hit **Connect**.
3. Either they tap **Accept** (on-demand), or it connects straight away (if you set the
   unattended password).
4. You now **see their tablet screen**. With the control plug-in, you can **tap and type**
   for them - re-pair the printer, open Android Bluetooth settings, press buttons, etc.
5. Disconnect when done.

### Example: fixing the Bluetooth printer remotely
- Connect to the tablet → you see their screen.
- Power-cycle the printer (ask them to flick it off/on - that part is physical).
- Open **Android Settings → Bluetooth**: if the printer is paired there, tap **Forget**.
- Back in ResDineOS → press **Print** → pick the printer when the chooser appears.
- Run **Dashboard → Printer Setup → Bluetooth diagnostics** to confirm it's green.

---

## Notes
- **Android tablets only** - iPads can't be controlled this way (and can't run the printer
  stuff anyway).
- The one thing you still can't do remotely is **physically** touch the printer (turn it
  off/on, plug it in) - a quick "flick it off and on" to whoever's there covers that.
- Keep your address list + any unattended passwords somewhere safe - that access is
  powerful, so treat it like a key to the tablet.
- Free AnyDesk is fine to start; if you're supporting lots of tablets a lot, their cheap
  business plan removes the occasional "are you a business?" nudge.

---

## How this fits with the rest
- **Tablet problems (printer, Bluetooth, Android):** AnyDesk (this guide).
- **ResDineOS app/data (orders, menu, settings, till):** your **Super Admin** panel -
  the **Dashboard** and **Till** buttons on each restaurant open them in one click, from
  your own device. (Those buttons are visible to you only; restaurants never see them.)
- **Permanent fix for the dropping Bluetooth:** the **Printer Bridge app** (in this folder)
  once it's installed on the tablets.
