/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.data.plugin;

import net.rwhps.server.func.Prov;
import net.rwhps.server.io.GameInputStream;
import net.rwhps.server.io.GameOutputStream;
import net.rwhps.server.io.input.ReusableDisableSyncByteArrayInputStream;
import net.rwhps.server.io.output.ByteArrayOutputStream;
import net.rwhps.server.struct.ObjectMap;
import net.rwhps.server.struct.OrderedMap;
import net.rwhps.server.struct.SerializerTypeAll;
import net.rwhps.server.util.IsUtil;
import net.rwhps.server.util.alone.annotations.NeedToRefactor;
import net.rwhps.server.util.file.FileUtil;
import net.rwhps.server.util.log.Log;
import net.rwhps.server.util.log.exp.VariableException;
import net.rwhps.server.util.zip.gzip.GzipDecoder;
import net.rwhps.server.util.zip.gzip.GzipEncoder;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * [PluginData] 的默认实现. 使用 '{@link AbstractPluginData#setData}' 自带创建 [Value] 并跟踪其改动.
 * 实现注意
 * 此类型处于实验性阶段. 使用其中定义的属性和函数是安全的, 但将来可能会新增成员抽象函数.
 * @author RW-HPS/Dr
 */
@NeedToRefactor
@SuppressWarnings("unchecked")
class AbstractPluginData {
    private static final ObjectMap<Class<?>, SerializerTypeAll.TypeSerializer<?>> SERIALIZERS = new ObjectMap<>();
    private final OrderedMap<String, Value<?>> PLUGIN_DATA = new OrderedMap<>();
    private final ReusableDisableSyncByteArrayInputStream byteInputStream = new ReusableDisableSyncByteArrayInputStream();
    private final net.rwhps.server.io.output.ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    private final GameOutputStream dataOutput = new GameOutputStream(byteStream);
    private FileUtil fileUtil;

    static {
        DefaultSerializers.register();
    }

    /**
     * 这个 [PluginData] 保存时使用的文件.
     * @param fileUtil FileUtil
     */
    public void setFileUtil(FileUtil fileUtil) {
        this.fileUtil = fileUtil;
        fileUtil.createNewFile();
        this.read();
    }

    /**
     * 向PluginData中加入一个value
     * @param name value的名字
     * @param data 需要存储的数据
     * @param <T> data的类
     */
    public <T> void setData(String name,@NotNull final T data) {
        if (SERIALIZERS.containsKey(data.getClass())) {
            PLUGIN_DATA.put(name, new Value<>(data));
        } else {
            throw new VariableException.ObjectMapRuntimeException("UNSUPPORTED_SERIALIZATION");
        }
    }

    /**
     * 向PluginData中获取一个value
     * @param name value的名字
     * @param <T> data的类
     * @return value
     */
    public <T> T getData(String name) {
        return (T) PLUGIN_DATA.get(name).getData();
    }

    /**
     * 向PluginData中获取一个value
     * @param name value的名字
     * @param data 默认返回的数据
     * @param <T> data的类
     * @return value
     */
    public <T> T getData(String name,@NotNull final T data) {
        return (T) PLUGIN_DATA.get(name, () -> new Value<>(data)).getData();
    }

    public <T> T getData(String name,@NotNull final Prov<T> data) {
        return (T) PLUGIN_DATA.get(name, () -> new Value<>(data.get())).getData();
    }

    public void read() {
        if (IsUtil.isBlank(fileUtil) || fileUtil.notExists() || fileUtil.length() < 1) {
            return;
        }
        try (InputStream stream = fileUtil.getInputsStream()) {
            read(stream);
        } catch (Exception ignored) {
        }
    }

    public void read(InputStream inStream) {
        try (DataInputStream stream = new DataInputStream(GzipDecoder.getGzipInputStream(inStream))) {
            int amount = stream.readInt();

            for (int i = 0; i < amount; i++) {
                int length; byte[] bytes;
                String key = stream.readUTF();
                byte type = stream.readByte();
                switch (type) {
                    case 0:
                        PLUGIN_DATA.put(key, new Value<>(stream.readBoolean()));
                        break;
                    case 1:
                        PLUGIN_DATA.put(key, new Value<>(stream.readInt()));
                        break;
                    case 2:
                        PLUGIN_DATA.put(key, new Value<>(stream.readLong()));
                        break;
                    case 3:
                        PLUGIN_DATA.put(key, new Value<>(stream.readFloat()));
                        break;
                    case 4:
                        PLUGIN_DATA.put(key, new Value<>(stream.readUTF()));
                        break;
                    case 5:
                        /* 把String转为Class,来进行反序列化 */
                        Class<?> classCache = Class.forName(stream.readUTF().replace("net.rwhps.server","net.rwhps.server"));
                        length = stream.readInt();
                        bytes = new byte[length];
                        stream.read(bytes);
                        PLUGIN_DATA.put(key, new Value<>(getObject(classCache,bytes)));
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + type);
                }
            }
        } catch (EOFException e) {
            // 忽略
            Log.warn("数据文件为空 若第一次启动可忽略");
        } catch (Exception e) {
            Log.error("Read Data",e);
        }
    }

    public void save() {
        if (IsUtil.isBlank(fileUtil) || fileUtil.notExists()) {
            return;
        }
        try(DataOutputStream stream = new DataOutputStream(GzipEncoder.getGzipOutputStream(fileUtil.writeByteOutputStream(false)))){
            stream.writeInt(PLUGIN_DATA.size);

            for(ObjectMap.Entry<String, Value<?>> entry : PLUGIN_DATA.entries()){
                stream.writeUTF(entry.key);
                Object value = entry.value.getData();
                if(value instanceof Boolean){
                    stream.writeByte(0);
                    stream.writeBoolean((Boolean)value);
                }else if(value instanceof Integer){
                    stream.writeByte(1);
                    stream.writeInt((Integer)value);
                }else if(value instanceof Long){
                    stream.writeByte(2);
                    stream.writeLong((Long)value);
                }else if(value instanceof Float){
                    stream.writeByte(3);
                    stream.writeFloat((Float)value);
                }else if(value instanceof String){
                    stream.writeByte(4);
                    stream.writeUTF((String)value);
                }else {
                    try {
                        byte[] bytes = putBytes(value);
                        stream.writeByte(5);
                        /* 去除ToString后的前缀(class com.xxx~) */
                        stream.writeUTF(value.getClass().toString().replace("class ",""));
                        stream.writeInt(bytes.length);
                        stream.write(bytes);
                    } catch (IOException e) {
                        Log.error("Save Error",e);
                    }
                }
            }
            stream.flush();
        }catch(Exception e){
            fileUtil.getFile().delete();
            Log.error("Write Data",e);
            throw new RuntimeException();
        }
    }

    public void cleanRam() {
        PLUGIN_DATA.clear();
    }

    protected static SerializerTypeAll.TypeSerializer getSerializer(Class<?> type) {
        return SERIALIZERS.get(type);
    }

    protected static <T> void setSerializer(Class<?> type, SerializerTypeAll.TypeSerializer<T> ser) {
        SERIALIZERS.put(type, ser);
    }

    private  <T> T getObject(Class<T> type, byte[] bytes) {
        if (!SERIALIZERS.containsKey(type)) {
            Log.error(new IllegalArgumentException("Type " + type + " does not have a serializer registered!"));
            return null;
        }
        SerializerTypeAll.TypeSerializer<?> serializer = SERIALIZERS.get(type);
        try {
            this.byteInputStream.setBytes(bytes);
            Object obj = serializer.read(new GameInputStream(byteInputStream));
            if (obj == null) {
                return null;
            }
            return (T)obj;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] putBytes(Object value) throws IOException {
        return putBytes(value, value.getClass());
    }

    private byte[] putBytes(Object value, Class<?> type) throws IOException {
        if (!SERIALIZERS.containsKey(type)) {
            Log.error(new IllegalArgumentException("Type " + type + " does not have a serializer registered!"));
        }
        this.byteStream.reset();
        SerializerTypeAll.TypeSerializer<Object> serializer = (SerializerTypeAll.TypeSerializer)SERIALIZERS.get(type);
        serializer.write(this.dataOutput, value);
        return this.byteStream.toByteArray();
    }
}