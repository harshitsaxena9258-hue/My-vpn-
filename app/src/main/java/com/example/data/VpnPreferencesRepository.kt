package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GoogleUsaServers
import com.example.model.TunnelProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vpnPreferencesDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "pulse_vpn_prefs")

data class VpnUserPreferences(
  val selectedServerId: String = GoogleUsaServers.all.first().id,
  val protocol: TunnelProtocol = TunnelProtocol.DOH_HTTPS,
  val ipv6Enabled: Boolean = true,
)

class VpnPreferencesRepository(private val context: Context) {

  private object Keys {
    val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
    val PROTOCOL = stringPreferencesKey("tunnel_protocol")
    val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
  }

  val preferencesFlow: Flow<VpnUserPreferences> =
    context.vpnPreferencesDataStore.data.map { prefs ->
      val serverId = prefs[Keys.SELECTED_SERVER_ID] ?: GoogleUsaServers.all.first().id
      val protocolName = prefs[Keys.PROTOCOL] ?: TunnelProtocol.DOH_HTTPS.name
      val protocol =
        TunnelProtocol.entries.firstOrNull { it.name == protocolName }
          ?: TunnelProtocol.DOH_HTTPS
      val ipv6 = prefs[Keys.IPV6_ENABLED] ?: true

      VpnUserPreferences(
        selectedServerId = GoogleUsaServers.findById(serverId).id,
        protocol = protocol,
        ipv6Enabled = ipv6,
      )
    }

  suspend fun setSelectedServer(serverId: String) {
    context.vpnPreferencesDataStore.edit { prefs -> prefs[Keys.SELECTED_SERVER_ID] = serverId }
  }

  suspend fun setProtocol(protocol: TunnelProtocol) {
    context.vpnPreferencesDataStore.edit { prefs -> prefs[Keys.PROTOCOL] = protocol.name }
  }

  suspend fun setIpv6Enabled(enabled: Boolean) {
    context.vpnPreferencesDataStore.edit { prefs -> prefs[Keys.IPV6_ENABLED] = enabled }
  }
}
