import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class SmartHub {

    public static void main(String[] args) {
        if (args == null || args.length <= 1) {
            System.exit(0);
        }

        String url = args[0];
        String hex = args[1];
        int src = Integer.parseInt(hex, 16);

        SmartHub smartHub = new SmartHub(url, src);
        smartHub.start();
    }

    public class Cmd {
        public static int WHOISHERE = 0x01;
        public static int IAMHERE = 0x02;
        public static int GETSTATUS = 0x03;
        public static int STATUS = 0x04;
        public static int SETSTATUS = 0x05;
        public static int TICK = 0x06;
    }

    public interface CmdBodyBuilder<T> {
        T build(byte[] values, int offset, int length);
    }

    public static class CmdDevName {
        private String devName;

        public CmdDevName() {
        }

        public CmdDevName(String devName) {
            this.devName = devName;
        }

        public String getDevName() {
            return devName;
        }

        public void setDevName(String devName) {
            this.devName = devName;
        }
    }

    public static class CmdSensorStatus {
        private Long[] values;

        public CmdSensorStatus() {
        }

        public Long[] getValues() {
            return values;
        }

        public void setValues(Long[] values) {
            this.values = values;
        }
    }

    public static class CmdStatusValue {
        private int value;

        public CmdStatusValue() {
        }

        public CmdStatusValue(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class CmdSwitch {
        private String devName;
        private String[] devices;

        public CmdSwitch() {
        }

        public CmdSwitch(String devName, String[] devices) {
            this.devName = devName;
            this.devices = devices;
        }

        public String getDevName() {
            return devName;
        }

        public void setDevName(String devName) {
            this.devName = devName;
        }

        public String[] getDevices() {
            return devices;
        }

        public void setDevices(String[] devices) {
            this.devices = devices;
        }
    }

    public static class CmdTick {
        private long timestamp;

        public CmdTick() {
        }

        public CmdTick(long timestamp) {
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public interface Command {
        List<Payload> execute(SmartHub smartHub, Payload payload);
    }

    public class CRC8 {
        private static final int poly =  0x1D;
        private int crc = 0;

        public static long calculate(final byte[] input, final int offset, final int len) {
            int crc = 0;

            for (int i = 0; i < len; i++) {
                crc ^= input[offset + i];
                for (int j = 0; j < 8; j++) {
                    if ((crc & 0x80) != 0) {
                        crc = ((crc << 1) ^ poly);
                    } else {
                        crc <<= 1;
                    }
                }
                crc &= 0xFF;
            }
            return (crc & 0xFF);
        }

        public static long calculate(final List<Byte> input, final int offset, final int len) {
            int crc = 0;

            for (int i = 0; i < len; i++) {
                crc ^= input.get(offset + i).byteValue();
                for (int j = 0; j < 8; j++) {
                    if ((crc & 0x80) != 0) {
                        crc = ((crc << 1) ^ poly);
                    } else {
                        crc <<= 1;
                    }
                }
                crc &= 0xFF;
            }
            return (crc & 0xFF);
        }
    }

    public static class Device {
        private String name;
        private int devType;
        private long src;

        public Device() {
        }

        public Device(String name, int devType, long src) {
            this.name = name;
            this.devType = devType;
            this.src = src;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getSrc() {
            return src;
        }

        public void setSrc(long src) {
            this.src = src;
        }

        public int getDevType() {
            return devType;
        }

        public void setDevType(int devType) {
            this.devType = devType;
        }
    }

    public class Devices {
        public static int SMART_HUB = 0x01;
        public static int ENV_SENSOR  = 0x02;
        public static int SWITCH = 0x03;
        public static int LAMP = 0x04;
        public static int SOCKET = 0x05;
        public static int CLOCK = 0x06;
    }

    public static class DeviceSwitch extends DeviceValue {
        private String[] devices;

        public DeviceSwitch(String name, int devType, long src, int value, String[] devices) {
            super(name, devType, src, value);
            this.devices = devices;
        }

        public String[] getDevices() {
            return devices;
        }

        public void setDevices(String[] devices) {
            this.devices = devices;
        }
    }

    public static class DeviceValue extends Device {
        private int value;

        public DeviceValue(String name, int devType, long src, int value) {
            super(name, devType, src);
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class Packet {
        private int length;
        private Payload payload;
        private int crc8;

        public Packet() {
        }

        public Packet(int length, Payload payload, int crc8) {
            this.length = length;
            this.payload = payload;
            this.crc8 = crc8;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public Payload getPayload() {
            return payload;
        }

        public void setPayload(Payload payload) {
            this.payload = payload;
        }

        public int getCrc8() {
            return crc8;
        }

        public void setCrc8(int crc8) {
            this.crc8 = crc8;
        }
    }

    public static class Payload<T> {
        private long src;
        private long dst;
        private long serial;
        private int devType;
        private int cmd;
        private T cmdBody;

        public Payload() {
        }

        public Payload(long src, long dst, long serial, int devType, int cmd, T cmdBody) {
            this.src = src;
            this.dst = dst;
            this.serial = serial;
            this.devType = devType;
            this.cmd = cmd;
            this.cmdBody = cmdBody;
        }

        public long getSrc() {
            return src;
        }

        public void setSrc(long src) {
            this.src = src;
        }

        public long getDst() {
            return dst;
        }

        public void setDst(long dst) {
            this.dst = dst;
        }

        public long getSerial() {
            return serial;
        }

        public void setSerial(long serial) {
            this.serial = serial;
        }

        public int getDevType() {
            return devType;
        }

        public void setDevType(int devType) {
            this.devType = devType;
        }

        public int getCmd() {
            return cmd;
        }

        public void setCmd(int cmd) {
            this.cmd = cmd;
        }

        public T getCmdBody() {
            return cmdBody;
        }

        public void setCmdBody(T cmdBody) {
            this.cmdBody = cmdBody;
        }
    }

    public static class PayloadConverter {
        private static final Map<String, CmdBodyBuilder> BODY_BUILDER = new HashMap<>();

        static {
            BODY_BUILDER.put(Devices.SMART_HUB + ":" + Cmd.WHOISHERE, new CmdDevNameBuilder()); // SmartHub, WHOISHERE (1, 1)
            BODY_BUILDER.put(Devices.SMART_HUB + ":" + Cmd.IAMHERE, new CmdDevNameBuilder()); // SmartHub, IAMHERE (1, 2)

            BODY_BUILDER.put(Devices.ENV_SENSOR + ":" + Cmd.WHOISHERE, new CmdDevNameBuilder()); // EnvSensor, WHOISHERE (2, 1)
            BODY_BUILDER.put(Devices.ENV_SENSOR + ":" + Cmd.IAMHERE, new CmdDevNameBuilder()); // EnvSensor, IAMHERE (2, 2)
            BODY_BUILDER.put(Devices.ENV_SENSOR + ":" + Cmd.STATUS, new CmdSensorStatusBuilder()); // EnvSensor, STATUS (2, 4)

            BODY_BUILDER.put(Devices.SWITCH + ":" + Cmd.WHOISHERE, new CmdSwitchBuilder());  // Switch, WHOISHERE (3, 1)
            BODY_BUILDER.put(Devices.SWITCH + ":" + Cmd.IAMHERE, new CmdSwitchBuilder());  // Switch, IAMHERE (3, 2)
            BODY_BUILDER.put(Devices.SWITCH + ":" + Cmd.STATUS, new CmdStatusValueBuilder()); // Switch, STATUS (3, 4)

            BODY_BUILDER.put(Devices.LAMP + ":" + Cmd.WHOISHERE, new CmdDevNameBuilder()); // Lamp, WHOISHERE (4, 1)
            BODY_BUILDER.put(Devices.LAMP + ":" + Cmd.IAMHERE, new CmdDevNameBuilder()); // Lamp, IAMHERE (4, 2)
            BODY_BUILDER.put(Devices.LAMP + ":" + Cmd.STATUS, new CmdStatusValueBuilder()); // Lamp, STATUS (4, 4)

            BODY_BUILDER.put(Devices.SOCKET + ":" + Cmd.WHOISHERE, new CmdDevNameBuilder()); // Socket, WHOISHERE (5, 1)
            BODY_BUILDER.put(Devices.SOCKET + ":" + Cmd.IAMHERE, new CmdDevNameBuilder()); // Socket, IAMHERE (5, 2)
            BODY_BUILDER.put(Devices.SOCKET + ":" + Cmd.STATUS, new CmdStatusValueBuilder()); // Socket, STATUS (5, 4)

            BODY_BUILDER.put(Devices.CLOCK + ":" + Cmd.IAMHERE, new CmdDevNameBuilder()); // Clock, IAMHERE (6, 2)
            BODY_BUILDER.put(Devices.CLOCK + ":" + Cmd.TICK, new CmdTickBuilder());// Clock, TICK (6, 6)
        }

        private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
        private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

        public List<Packet> fromBase64(String base64String) {
            List<Packet> packets = new ArrayList<>();
            try {
                byte[] values = BASE64_URL_DECODER.decode(base64String);

                int startPos = 0;
                while (startPos < values.length) {
                    Packet packet = getPacket(values, startPos);
                    if (packet.getPayload() != null) {
                        packets.add(packet);
                    }
                    startPos += packet.getLength() + 2;
                }
            } catch (Exception e) {
                // игнорируем сбойные пакеты
            }
            return packets;
        }

        private Packet getPacket(byte[] values, int startPos) {
            Packet packet = new Packet();
            packet.setLength(Byte.toUnsignedInt(values[startPos]));
            packet.setCrc8(Byte.toUnsignedInt(values[startPos + packet.getLength() + 1]));
            long checkCrc8 = CRC8.calculate(values, startPos + 1, packet.getLength());
            if (packet.getCrc8() != ((int) checkCrc8)) {
                return packet;
            }


            int startValuePos = startPos + 1;
            int currentOffset = 0;

            Payload payload = new Payload();

            Varuint varuint = VarUtils.getVaruint(values, startValuePos + currentOffset, packet.getLength() - currentOffset);
            payload.setSrc(varuint.getValue());
            currentOffset += varuint.getSize();

            varuint = VarUtils.getVaruint(values, startValuePos + currentOffset, packet.getLength() - currentOffset);
            payload.setDst(varuint.getValue());
            currentOffset += varuint.getSize();

            varuint = VarUtils.getVaruint(values, startValuePos + currentOffset, packet.getLength() - currentOffset);
            payload.setSerial(varuint.getValue());
            currentOffset += varuint.getSize();

            payload.setDevType(Byte.toUnsignedInt(values[startValuePos + currentOffset]));
            ++currentOffset;

            payload.setCmd(Byte.toUnsignedInt(values[startValuePos + currentOffset]));
            ++currentOffset;

            CmdBodyBuilder cmdBodyBuilder = BODY_BUILDER.get(payload.getDevType() + ":" + payload.getCmd());
            if (cmdBodyBuilder != null) {
                try {
                    payload.setCmdBody(cmdBodyBuilder.build(values, startValuePos + currentOffset, packet.getLength() - currentOffset));
                } catch (Exception e) {
                    // обработка возможных ошибок
                }
            }
            packet.setPayload(payload);
            return packet;
        }

        public String toBase64(List<Payload> payloads) {
            List<Byte> bytes = new ArrayList<>();
            int start = 0;
            for (Payload payload : payloads) {
                write(bytes, payload.getSrc());
                write(bytes, payload.getDst());
                write(bytes, payload.getSerial());
                write(bytes, payload.getDevType());
                write(bytes, payload.getCmd());
                if (payload.getCmdBody() instanceof CmdDevName body) {
                    write(bytes, body.getDevName());
                } else if (payload.getCmdBody() instanceof CmdStatusValue body) {
                    write(bytes, body.getValue());
                }

                int length = bytes.size() - start;
                bytes.add(start, (byte) length);
                long crc8 = CRC8.calculate(bytes, start + 1, length);
                bytes.add((byte) crc8);

                start += length + 2;
            }

            byte[] resultBytes = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                resultBytes[i] = bytes.get(i).byteValue();
            }
            return BASE64_URL_ENCODER.encodeToString(resultBytes);
        }

        private static void write(List<Byte> bytes, long value) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            int count = ULEB128.write(byteBuffer, value);
            for (int i = 0; i < count; i++) {
                bytes.add(byteBuffer.get(i));
            }
        }

        private static void write(List<Byte> bytes, int value) {
            bytes.add((byte) value);
        }

        private static void write(List<Byte> bytes, String value) {
            byte[] results = value.getBytes(StandardCharsets.US_ASCII);
            bytes.add((byte) results.length);
            for (int i = 0; i < results.length; i++) {
                bytes.add(results[i]);
            }
        }


        // --------------

        public static class CmdDevNameBuilder implements CmdBodyBuilder<CmdDevName> {
            @Override
            public CmdDevName build(byte[] values, int offset, int length) {
                CmdDevName cmdDevName = new CmdDevName();
                VarString value = VarUtils.getString(values, offset);
                cmdDevName.setDevName(value.getValue());
                return cmdDevName;
            }
        }

        public static class CmdStatusValueBuilder implements CmdBodyBuilder<CmdStatusValue> {
            @Override
            public CmdStatusValue build(byte[] values, int offset, int length) {
                int value = Byte.toUnsignedInt(values[offset]);
                return new CmdStatusValue(value);
            }
        }

        public static class CmdSwitchBuilder implements CmdBodyBuilder<CmdSwitch> {
            @Override
            public CmdSwitch build(byte[] values, int offset, int length) {
                CmdSwitch cmdSwitch = new CmdSwitch();
                VarString value = VarUtils.getString(values, offset);
                cmdSwitch.setDevName(value.getValue());
                offset += value.getSize();

                int size = Byte.toUnsignedInt(values[offset]);
                ++offset;

                List<String> devices = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    VarString device = VarUtils.getString(values, offset);
                    devices.add(device.getValue());
                    offset += device.getSize();
                }
                cmdSwitch.setDevices(devices.toArray(new String[devices.size()]));
                return cmdSwitch;
            }
        }

        public static class CmdSensorStatusBuilder implements CmdBodyBuilder<CmdSensorStatus> {
            @Override
            public CmdSensorStatus build(byte[] values, int offset, int length) {
                return new CmdSensorStatus();
            }
        }


        public static class CmdTickBuilder implements CmdBodyBuilder<CmdTick> {
            @Override
            public CmdTick build(byte[] values, int offset, int length) {
                CmdTick cmdTick = new CmdTick();
                Varuint varuint = VarUtils.getVaruint(values, offset, length);
                cmdTick.setTimestamp(varuint.getValue());
                return cmdTick;
            }
        }
    }

    public static class SmartHub {
        private static final int DST_ALL = 0x3FFF;
        private static final PayloadConverter PAYLOAD_CONVERTER = new PayloadConverter();
        private static final Duration TIMEOUT = Duration.ofMillis(60000);

        private static final Map<String, Command> COMMANDS = new HashMap<>();

        static {
            COMMANDS.put(Devices.ENV_SENSOR + ":" + Cmd.WHOISHERE, new SensorWhoIsHereCommand());  // Sensor, WHOISHERE (2, 1)
            COMMANDS.put(Devices.ENV_SENSOR + ":" + Cmd.IAMHERE, new SensorIamHereCommand());  // Sensor, IAMHERE (2, 2)
            COMMANDS.put(Devices.ENV_SENSOR + ":" + Cmd.STATUS, new SensorStatusCommand()); // Sensor, STATUS (2, 4)

            COMMANDS.put(Devices.SWITCH + ":" + Cmd.WHOISHERE, new SwitchDeviceWhoIsHereCommand());  // Switch, WHOISHERE (3, 1)
            COMMANDS.put(Devices.SWITCH + ":" + Cmd.IAMHERE, new SwitchDeviceIamHereCommand());  // Switch, IAMHERE (3, 2)
            COMMANDS.put(Devices.SWITCH + ":" + Cmd.STATUS, new SwitchStatusCommand()); // Switch, STATUS (3, 4)

            COMMANDS.put(Devices.LAMP + ":" + Cmd.WHOISHERE, new DeviceWhoIsHereCommand());  // Lamp, WHOISHERE (4, 1)
            COMMANDS.put(Devices.LAMP + ":" + Cmd.IAMHERE, new DeviceValueIamHereCommand());  // Lamp, IAMHERE (4, 2)
            COMMANDS.put(Devices.LAMP + ":" + Cmd.STATUS, new DeviceValueStatusCommand()); // Lamp, STATUS (4, 4)

            COMMANDS.put(Devices.SOCKET + ":" + Cmd.WHOISHERE, new DeviceWhoIsHereCommand());  // Lamp, WHOISHERE (4, 1)
            COMMANDS.put(Devices.SOCKET + ":" + Cmd.IAMHERE, new DeviceValueIamHereCommand());  // Socket, IAMHERE (5, 2)
            COMMANDS.put(Devices.SOCKET + ":" + Cmd.STATUS, new DeviceValueStatusCommand()); // Socket, STATUS (5, 4)

//        COMMANDS.put(Devices.CLOCK + ":" + Cmd.WHOISHERE, new DeviceWhoIsHereCommand());  // Clock, WHOISHERE (6, 1)
            COMMANDS.put(Devices.CLOCK + ":" + Cmd.IAMHERE, new EmptyCommand());// Clock, IAMHERE (6, 2)
            COMMANDS.put(Devices.CLOCK + ":" + Cmd.TICK, new TickCommand()); // Clock, TICK (6, 6)
        }

        private String url;
        private int src;
        private long serial = 1;
        private HttpClient client;
        private long currentTime = 0;

        private Map<Long, Device> srcDevices = new HashMap<>();
        private Map<String, Device> devices = new HashMap<>();
        private List<StatusRequest> statusRequests = new LinkedList<>();

        public SmartHub(String url, int src) {
            this.url = url;
            this.src = src;
        }

        public void start() {
            client = HttpClient.newHttpClient();
            HttpRequest request = buildStartRequest();


            int statusCode = 200;
            while (statusCode == 200) {
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 204) {
                        System.exit(0);
                    }
                    if (response.statusCode() != 200) {
                        System.exit(99);
                    }
                    statusCode = response.statusCode();
                    List<Packet> packets = PAYLOAD_CONVERTER.fromBase64(response.body());

                    List<Payload> requestPayloads = new ArrayList<>();
                    for (Packet packet : packets) {
                        Payload payload = packet.getPayload();
                        Command command = COMMANDS.get(payload.getDevType() + ":" + payload.getCmd());
                        if (command != null) {
                            try {
                                List<Payload> execute = command.execute(this, payload);
                                if (execute != null && !execute.isEmpty()) {
                                    requestPayloads.addAll(execute);
                                }
                            } catch (Exception e) {
                                // обработка возможных ошибок
                            }
                        }
                    }

                    HttpRequest.BodyPublisher bodyPublisher = requestPayloads.isEmpty() ?
                            HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(PAYLOAD_CONVERTER.toBase64(requestPayloads));

                    request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(TIMEOUT)
                            .POST(bodyPublisher)
                            .build();

                } catch (Exception e) {
//                System.out.println(e);
                    System.exit(99);
                }
            }
        }

        private HttpRequest buildStartRequest() {
            Payload payload = buidWhoishere();
            String packet = PAYLOAD_CONVERTER.toBase64(List.of(payload));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(packet))
                    .build();
            return request;
        }

        public void removeFromStatusRequest(long src) {
            ListIterator<StatusRequest> statusRequestListIterator = statusRequests.listIterator();
            while (statusRequestListIterator.hasNext()) {
                StatusRequest statusRequest = statusRequestListIterator.next();
                if (statusRequest.getSrc() == src) {
                    statusRequestListIterator.remove();
                }
            }
        }

        public void deleteDevicesByTiming(long currentTime) {
            ListIterator<StatusRequest> statusRequestListIterator = statusRequests.listIterator();
            while (statusRequestListIterator.hasNext()) {
                StatusRequest statusRequest = statusRequestListIterator.next();
                if (statusRequest.getRequestTime() + 300 < currentTime) {
                    statusRequestListIterator.remove();
                    removeDevice(statusRequest.getSrc());
                } else {
                    break;
                }
            }
        }

        public Payload buidWhoishere() {
            return new Payload(src, DST_ALL, serial++, Devices.SMART_HUB, Cmd.WHOISHERE, new CmdDevName("HUB01"));
        }

        public Payload buidIamhere() {
            return new Payload(src, DST_ALL, serial++, Devices.SMART_HUB, Cmd.IAMHERE, new CmdDevName("HUB01"));
        }

        public Payload buidGetStatus(int devType, long dst) {
            statusRequests.add(new StatusRequest(dst, currentTime));
            return new Payload(src, dst, serial++, devType, Cmd.GETSTATUS, null);
        }

        public Payload buidSetStatus(int devType, long dst, int value) {
            statusRequests.add(new StatusRequest(dst, currentTime));
            return new Payload(src, dst, serial++, devType, Cmd.SETSTATUS, new CmdStatusValue(value));
        }

        public void addDevice(Device device) {
            devices.put(device.getName(), device);
            srcDevices.put(device.getSrc(), device);
        }

        public void removeDevice(long src) {
            Device device = srcDevices.remove(src);
            if (device != null) {
                devices.remove(device.getName());
            }
        }

        public Device getDeviceByName(String name) {
            return devices.get(name);
        }

        public Device getDeviceBySrc(long src) {
            return srcDevices.get(src);
        }

        public long getCurrentTime() {
            return currentTime;
        }

        public void setCurrentTime(long currentTime) {
            this.currentTime = currentTime;
        }

        public int getSrc() {
            return src;
        }

        public void setSrc(int src) {
            this.src = src;
        }

        static class EmptyCommand implements Command {
            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                return null;
            }
        }

        static class DeviceWhoIsHereCommand implements Command {

            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != DST_ALL) {
                    return null;
                }
                DeviceValue device = new DeviceValue(((CmdDevName) payload.getCmdBody()).getDevName(), payload.getDevType(), payload.getSrc(), 0);
                smartHub.addDevice(device);

                return List.of(smartHub.buidIamhere(), smartHub.buidGetStatus(device.getDevType(), device.getSrc()));
            }
        }

        static class SensorWhoIsHereCommand implements Command {

            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != DST_ALL) {
                    return null;
                }
                Device device = new Device(((CmdDevName) payload.getCmdBody()).getDevName(), payload.getDevType(), payload.getSrc());
                smartHub.addDevice(device);

                return List.of(smartHub.buidIamhere(), smartHub.buidGetStatus(device.getDevType(), device.getSrc()));
            }
        }

        static class SensorIamHereCommand implements Command {

            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != DST_ALL) {
                    return null;
                }
                Device device = new Device(((CmdDevName) payload.getCmdBody()).getDevName(), payload.getDevType(), payload.getSrc());
                smartHub.addDevice(device);

                return List.of(smartHub.buidGetStatus(device.getDevType(), device.getSrc()));
            }
        }

        static class SensorStatusCommand implements Command {

            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != smartHub.getSrc()) {
                    return null;
                }

                smartHub.removeFromStatusRequest(payload.getSrc());

//            CmdSensorStatus cmdStatusValue = (CmdSensorStatus) payload.getCmdBody();
//            Device deviceBySrc = smartHub.getDeviceBySrc(payload.getSrc());

                return null;
            }
        }

        static class DeviceValueIamHereCommand implements Command {

            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != DST_ALL) {
                    return null;
                }
                DeviceValue device = new DeviceValue(((CmdDevName) payload.getCmdBody()).getDevName(), payload.getDevType(), payload.getSrc(), 0);
                smartHub.addDevice(device);

                return List.of(smartHub.buidGetStatus(device.getDevType(), device.getSrc()));
            }
        }


        static class DeviceValueStatusCommand implements Command {

            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != smartHub.getSrc()) {
                    return null;
                }

                smartHub.removeFromStatusRequest(payload.getSrc());

                CmdStatusValue cmdStatusValue = (CmdStatusValue) payload.getCmdBody();
                Device deviceBySrc = smartHub.getDeviceBySrc(payload.getSrc());
                if (deviceBySrc instanceof DeviceValue deviceValue) {
                    deviceValue.setValue(cmdStatusValue.getValue());
                }


                return null;
            }
        }

        static class SwitchDeviceWhoIsHereCommand implements Command {
            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != DST_ALL) {
                    return null;
                }
                CmdSwitch cmdSwitch = (CmdSwitch) payload.getCmdBody();

                DeviceSwitch device = new DeviceSwitch(cmdSwitch.getDevName(), payload.getDevType(), payload.getSrc(), 0, cmdSwitch.getDevices());
                smartHub.addDevice(device);

                return List.of(smartHub.buidIamhere(), smartHub.buidGetStatus(device.getDevType(), device.getSrc()));
            }
        }

        static class SwitchDeviceIamHereCommand implements Command {
            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != DST_ALL) {
                    return null;
                }
                CmdSwitch cmdSwitch = (CmdSwitch) payload.getCmdBody();

                DeviceSwitch device = new DeviceSwitch(cmdSwitch.getDevName(), payload.getDevType(), payload.getSrc(), 0, cmdSwitch.getDevices());
                smartHub.addDevice(device);

                return List.of(smartHub.buidGetStatus(device.getDevType(), device.getSrc()));
            }
        }

        static class SwitchStatusCommand implements Command {
            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                if (payload.getDst() != smartHub.getSrc()) {
                    return null;
                }

                smartHub.removeFromStatusRequest(payload.getSrc());

                List<Payload> payloads = new ArrayList<>();
                CmdStatusValue cmdStatusValue = (CmdStatusValue) payload.getCmdBody();
                Device deviceBySrc = smartHub.getDeviceBySrc(payload.getSrc());
                if (deviceBySrc instanceof DeviceSwitch deviceSwitch) {
                    deviceSwitch.setValue(cmdStatusValue.getValue());
                    if (deviceSwitch.getDevices() != null) {
                        for (String deviceName : deviceSwitch.getDevices()) {
                            Device device = smartHub.getDeviceByName(deviceName);
                            if (device instanceof DeviceValue deviceValue) {
//                            deviceValue.setValue(cmdStatusValue.getValue());
                                payloads.add(smartHub.buidSetStatus(deviceValue.getDevType(), deviceValue.getSrc(), cmdStatusValue.getValue()));
                            }
                        }
                    }
                }
                return payloads;
            }
        }

        static class TickCommand implements Command {
            @Override
            public List<Payload> execute(SmartHub smartHub, Payload payload) {
                CmdTick cmdTick = (CmdTick) payload.getCmdBody();
                smartHub.setCurrentTime(cmdTick.getTimestamp());

                smartHub.deleteDevicesByTiming(cmdTick.getTimestamp());

                return null;
            }
        }

        private class StatusRequest {
            private long src;
            private long requestTime;

            public StatusRequest(long src, long requestTime) {
                this.src = src;
                this.requestTime = requestTime;
            }

            public long getSrc() {
                return src;
            }

            public void setSrc(long src) {
                this.src = src;
            }

            public long getRequestTime() {
                return requestTime;
            }

            public void setRequestTime(long requestTime) {
                this.requestTime = requestTime;
            }
        }
    }

    public class ULEB128 {

        public static int size(long value) {
            int groupCount = 0;
            long valueToSize = value;

            do {
                groupCount++;
                valueToSize >>>= 7;
            } while (valueToSize != 0);

            return groupCount;
        }

        public static int write(ByteBuffer byteBuffer, long value) {
            long valueToWrite = value;
            int bytesWritten = 0;

            do {
                byte groupValue = (byte) (valueToWrite & 0x7F);
                valueToWrite >>>= 7;
                if (valueToWrite != 0) {
                    groupValue |= 0x80;
                }

                byteBuffer.put(groupValue);
                bytesWritten++;
            } while (valueToWrite != 0);

            return bytesWritten;
        }

        public static long read(ByteBuffer byteBuffer) {
            long value = 0;
            int bytesRead = 0;
            boolean continueReading;

            do {
                final byte rawByteValue = byteBuffer.get();
                if (bytesRead == 9 && (rawByteValue & ~0x1) != 0) {
                    throw new IllegalStateException("ULEB128 sequence exceeds 64bits");
                }

                value |= (rawByteValue & 0x7FL) << (bytesRead * 7);

                bytesRead++;
                continueReading = ((rawByteValue & 0x80) != 0);
            } while (continueReading);

            return value;
        }
    }

    public static class VarString {
        private String value;
        private int size;

        public VarString(String value, int size) {
            this.value = value;
            this.size = size;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    public static class Varuint {
        private long value;
        private int size;

        public Varuint(long value, int size) {
            this.value = value;
            this.size = size;
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    public static class VarUtils {

        public static Varuint getVaruint(byte[] values, int offset, int length) {
            long value = ULEB128.read(ByteBuffer.wrap(values, offset, length));
            return new Varuint(value, ULEB128.size(value));
        }

        public static VarString getString(byte[] values, int offset) {
            int length = Byte.toUnsignedInt(values[offset]);
            String value = new String(Arrays.copyOfRange(values, offset + 1, offset + 1 + length), StandardCharsets.US_ASCII);
            return new VarString(value, length + 1);
        }

    }


}
