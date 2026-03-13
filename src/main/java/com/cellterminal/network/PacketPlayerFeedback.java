package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.gui.overlay.MessageType;


/**
 * Server -> Client packet for user-facing feedback messages.
 * <p>
 * This allows server-side code to display messages via the existing GUI overlay
 * and chat path on the client, without referencing client-only classes server-side.
 * <p>
 * Supports ITextComponent arguments, which are serialized as JSON and translated
 * client-side to ensure proper localization.
 */
public class PacketPlayerFeedback implements IMessage {

    /**
     * Prefix marker for serialized ITextComponent args.
     * When an arg starts with this, it's deserialized as ITextComponent on the client.
     */
    private static final String TEXT_COMPONENT_PREFIX = "\u0000ITC:";

    private MessageType type = MessageType.ERROR;
    private boolean raw;
    private String keyOrMessage = "";
    private String[] args = new String[0];

    public PacketPlayerFeedback() {
    }

    public PacketPlayerFeedback(MessageType type, String translationKey, Object... args) {
        this.type = type;
        this.raw = false;
        this.keyOrMessage = translationKey == null ? "" : translationKey;
        this.args = stringify(args);
    }

    public PacketPlayerFeedback(MessageType type, String rawMessage) {
        this.type = type;
        this.raw = true;
        this.keyOrMessage = rawMessage == null ? "" : rawMessage;
        this.args = new String[0];
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.raw = buf.readBoolean();

        int typeOrdinal = buf.readInt();
        MessageType[] values = MessageType.values();
        this.type = typeOrdinal >= 0 && typeOrdinal < values.length ? values[typeOrdinal] : MessageType.ERROR;

        this.keyOrMessage = ByteBufUtils.readUTF8String(buf);

        int argCount = buf.readInt();
        if (argCount < 0) {
            this.args = new String[0];

            return;
        }

        this.args = new String[argCount];
        for (int i = 0; i < argCount; i++) this.args[i] = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.raw);
        buf.writeInt(this.type.ordinal());
        ByteBufUtils.writeUTF8String(buf, this.keyOrMessage);
        buf.writeInt(this.args.length);

        for (String arg : this.args) {
            ByteBufUtils.writeUTF8String(buf, arg == null ? "" : arg);
        }
    }

    private static String[] stringify(Object[] values) {
        if (values == null || values.length == 0) return new String[0];

        String[] strings = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];

            if (value == null) {
                strings[i] = "null";
            } else if (value instanceof ITextComponent) {
                // Serialize ITextComponent as JSON with prefix marker for client-side deserialization
                strings[i] = TEXT_COMPONENT_PREFIX + ITextComponent.Serializer.componentToJson((ITextComponent) value);
            } else {
                strings[i] = String.valueOf(value);
            }
        }

        return strings;
    }

    /**
     * Deserialize args on the client side.
     * Converts serialized ITextComponent args back to their formatted text.
     */
    @SideOnly(Side.CLIENT)
    private static Object[] deserializeArgs(String[] args) {
        if (args == null || args.length == 0) return new Object[0];

        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg != null && arg.startsWith(TEXT_COMPONENT_PREFIX)) {
                // Deserialize ITextComponent from JSON and get formatted (translated) text
                String json = arg.substring(TEXT_COMPONENT_PREFIX.length());

                try {
                    ITextComponent component = ITextComponent.Serializer.jsonToComponent(json);
                    result[i] = component != null ? component.getFormattedText() : arg;
                } catch (Exception e) {
                    // Fallback to raw string if deserialization fails
                    result[i] = arg;
                }
            } else {
                result[i] = arg;
            }
        }

        return result;
    }

    public static class Handler implements IMessageHandler<PacketPlayerFeedback, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPlayerFeedback message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.raw) {
                    switch (message.type) {
                        case SUCCESS:
                            MessageHelper.successRaw(message.keyOrMessage);
                            break;
                        case WARNING:
                            MessageHelper.warningRaw(message.keyOrMessage);
                            break;
                        case ERROR:
                        default:
                            MessageHelper.errorRaw(message.keyOrMessage);
                            break;
                    }

                    return;
                }

                // Deserialize ITextComponent args to properly localized strings
                Object[] deserializedArgs = deserializeArgs(message.args);

                switch (message.type) {
                    case SUCCESS:
                        MessageHelper.success(message.keyOrMessage, deserializedArgs);
                        break;
                    case WARNING:
                        MessageHelper.warning(message.keyOrMessage, deserializedArgs);
                        break;
                    case ERROR:
                    default:
                        MessageHelper.error(message.keyOrMessage, deserializedArgs);
                        break;
                }
            });

            return null;
        }
    }
}