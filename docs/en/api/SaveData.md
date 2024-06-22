# RW-HPS - SaveData API

## Plugin Data Storage

### Design Goals

- Source code-level static typing: Avoid `getString()`, `getList()`...
- Automatic loading and saving: Plugins only need to link for automatic saving during startup.
- Real-time synchronization with frontend modifications: In graphical frontends like Android, changes can be dynamically synchronized in memory.
- Storage extensibility: Multiple storage methods can be used, whether files or databases, with the plugin layer using the same implementation approach.

In summary, **minimize the effort plugin authors need to put into handling data and configurations**.

*No support for database storage yet, but it's on the agenda.*

## [`Value`]

```java
interface Value<T> {
    private T data;
    
    protected Value(T data) {
        this.data = data;
    }
}
```

Represents a value proxy. In [`PluginData`], values are wrapped by [`Value`].

## [`PluginData`]

An internal data object within a plugin, hidden from users. Similar to a map where property names serve as keys and corresponding [`Value`]s serve as values.

[`PluginData`] interface has a base implementation class, [`AbstractPluginData`], which does not support automatic saving by default, only storing key-value relationships and their serializers.

Plugins can inherit from [`AbstractPluginData`] for high freedom in accessing implementation details and extending data structures.  
However, typically, plugins use [`AutoSavePluginData`].

[`AutoSavePluginData`] listens for modifications to values saved within it and starts coroutines to save data at appropriate times within the provided [`AutoSavePluginDataHolder`] coroutine scope.

### Using `PluginData`

Example is more efficient than theory at this moment

1. Plugin creates its own

```java
public class Main extends Plugin {
    PluginData pluginData = new PluginData();

    /**
     * Mainly for initialization here
     */
    @Override
    public void init() {
        // this.pluginDataFileUtil is automatically generated by extending Plugin
        // Set the location of the Bin file
        pluginData.setFileUtil(this.pluginDataFileUtil.toFile("ExampleData.bin"));
        pluginData.read();

        // Read
        long lastStartTime = this.pluginData.getData("lastStartTime", Time.concurrentMillis());
        String lastStartTimeString = this.pluginData.getData("lastStartTimeString", Time.getUtcMilliFormat(1));
        // Write
        this.pluginData.setData("lastStartTime", Time.concurrentMillis());
        this.pluginData.setData("lastStartTimeString", Time.getUtcMilliFormat(1));
    }
}
```