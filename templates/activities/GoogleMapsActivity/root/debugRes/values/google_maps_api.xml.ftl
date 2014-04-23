<resources>
    <#--
    NOTE: the merger does not merge comments (only NodeType == ELEMENT_NODE).
    Wrap it in an element as a workaround. -->
    <string name="google_maps_key_instructions" templateMergeStrategy="replace"><!--

    TODO: Before you run your application, you need a Google Maps API key.

    To get one, follow this link, follow the directions and press "Create" at the end:

https://console.developers.google.com/flows/enableapi?apiid=maps_android_backend&keyType=CLIENT_SIDE_ANDROID&r=${debugKeystoreSha1}%3B${packageName}

    You can also add your credentials to an existing key, using this line:
    ${debugKeystoreSha1};${packageName}

    Once you have your key (it starts with "AIza"), replace the "google_maps_key"
    string in this file.
    --></string>

    <#-- Always preserve the existing key. -->
    <string name="google_maps_key" templateMergeStrategy="preserve">YOUR_KEY_HERE</string>
</resources>
