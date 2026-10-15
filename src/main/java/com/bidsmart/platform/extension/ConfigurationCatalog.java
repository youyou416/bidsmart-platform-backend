package com.bidsmart.platform.extension;

import com.bidsmart.platform.permission.PermissionSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reserved configuration adapter contract; deliberately has no registered implementation or HTTP endpoint.
 * Implementations must validate catalog/key allowlists and the authenticated caller's authorization.
 * Values are data only: never resolve Java class names, execute expressions, SQL, or scripts from them.
 * Never expose process environment, credentials, cryptographic keys, or unrestricted server settings.
 */
public interface ConfigurationCatalog {
    List<DictionaryItem> dictionary(String dictionaryCode, PermissionSnapshot authenticatedCaller);

    Optional<String> parameter(String parameterKey, PermissionSnapshot authenticatedCaller);

    Map<String, String> businessConfiguration(String configurationCode, PermissionSnapshot authenticatedCaller);

    final class DictionaryItem {
        private final String code;
        private final String label;
        private final boolean enabled;

        public DictionaryItem(String code, String label, boolean enabled) {
            this.code = code;
            this.label = label;
            this.enabled = enabled;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public boolean isEnabled() { return enabled; }
    }
}
