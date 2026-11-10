# Rete: far parlare telefono e PC Hub

Le due opzioni sono gratuite. Il traffico è sempre protetto dal token API.

## Opzione A: Tailscale (consigliata, funziona anche fuori casa)

[Tailscale](https://tailscale.com/pricing) piano **Personal**: gratuito fino a 100 dispositivi, nessuna carta richiesta. Crea una VPN privata cifrata fra i tuoi dispositivi, quindi non serve aprire porte sul router.

1. Sul **PC Hub**: installa Tailscale da [tailscale.com/download](https://tailscale.com/download/windows) e accedi (Google, Microsoft o GitHub).
2. Sul **telefono**: installa Tailscale dal Play Store e accedi con lo **stesso account**.
3. Sul PC Hub annota l'IP Tailscale:

```powershell
tailscale ip -4      # es. 100.101.102.103
```

4. Nell'app Daily Hub imposta l'indirizzo `http://100.101.102.103:8765`.
5. Facoltativo: in Tailscale abilita **MagicDNS** e usa `http://<nome-pc>:8765`.

Firewall Windows: Tailscale crea un'interfaccia di rete propria. Se il telefono non raggiunge il PC, lancia una volta da PowerShell **come amministratore**:

```powershell
New-NetFirewallRule -DisplayName 'Daily Hub' -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Private,Domain
Get-NetConnectionProfile   # la rete di casa e "Tailscale" devono risultare Private
```

Nota: sul telefono Tailscale usa lo slot VPN di Android. Se usi già un'altra VPN, scegli l'opzione B.

## Opzione B: solo Wi-Fi di casa

1. Sul PC Hub trova l'IP locale: `ipconfig` -> "Indirizzo IPv4" (es. `192.168.1.20`).
2. Nel router assegna un **IP fisso (DHCP reservation)** al PC, così l'indirizzo non cambia.
3. Imposta in Windows la rete di casa come **Privata** e apri la porta come sopra.
4. Nell'app imposta `http://192.168.1.20:8765`.

Limite: fuori casa il widget mostra gli ultimi dati scaricati e "Offline". Le sveglie impostate fuori casa arrivano al PC entro circa 15 minuti dal rientro sul Wi-Fi, al successivo aggiornamento periodico. Puoi anche premere "Sincronizza sveglia ora" nell'app.

## Verifica

Dal browser del telefono apri `http://<indirizzo>:8765/api/health`: deve rispondere `{"status":"ok",...}`.
