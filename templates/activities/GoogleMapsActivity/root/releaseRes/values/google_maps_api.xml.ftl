<resources>
    <#--
    NOTE: the merger does not merge comments (only NodeType == ELEMENT_NODE).
    Wrap it in an element as a workaround. -->
    <string name="google_maps_key_instructions" templateMergeStrategy="replace"><!--

    TODO: Before you release your application, you need a Google Maps API key.

    To do this, you can either add your release key credentials to your existing
    key, or create a new key.

    Follow the directions here:

https://developers.google.com/maps/documentation/android/start#get_an_android_certificate_and_the_google_maps_api_key

    Once you have your key (it starts with "AIza"), replace the "google_maps_key"
    string in this file.
    --></string>

    <#-- Always preserve the existing key. -->
    <string name="google_maps_key" templateMergeStrategy="preserve">YOUR_KEY_HERE</string>
</resources>
