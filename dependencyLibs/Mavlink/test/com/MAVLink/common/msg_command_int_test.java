/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by the
 * java mavlink generator tool. It should not be modified by hand.
 */
         
// MESSAGE COMMAND_INT PACKING
package com.MAVLink.common;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Parser;
import com.MAVLink.ardupilotmega.CRC;
import java.nio.ByteBuffer;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
* Message encoding a command with parameters as scaled integers. Scaling depends on the actual command value.
*/
public class msg_command_int_test{

public static final int MAVLINK_MSG_ID_COMMAND_INT = 75;
public static final int MAVLINK_MSG_LENGTH = 35;
private static final long serialVersionUID = MAVLINK_MSG_ID_COMMAND_INT;

private Parser parser = new Parser();

public CRC generateCRC(byte[] packet){
    CRC crc = new CRC();
    for (int i = 1; i < packet.length - 2; i++) {
        crc.update_checksum(packet[i] & 0xFF);
    }
    crc.finish_checksum(MAVLINK_MSG_ID_COMMAND_INT);
    return crc;
}

public byte[] generateTestPacket(){
    ByteBuffer payload = ByteBuffer.allocate(6 + MAVLINK_MSG_LENGTH + 2);
    payload.put((byte)MAVLinkPacket.MAVLINK_STX); //stx
    payload.put((byte)MAVLINK_MSG_LENGTH); //len
    payload.put((byte)0); //seq
    payload.put((byte)255); //sysid
    payload.put((byte)190); //comp id
    payload.put((byte)MAVLINK_MSG_ID_COMMAND_INT); //msg id
    payload.putFloat((float)17.0); //param1
    payload.putFloat((float)45.0); //param2
    payload.putFloat((float)73.0); //param3
    payload.putFloat((float)101.0); //param4
    payload.putInt((int)963498296); //x
    payload.putInt((int)963498504); //y
    payload.putFloat((float)185.0); //z
    payload.putShort((short)18691); //command
    payload.put((byte)223); //target_system
    payload.put((byte)34); //target_component
    payload.put((byte)101); //frame
    payload.put((byte)168); //current
    payload.put((byte)235); //autocontinue
    
    CRC crc = generateCRC(payload.array());
    payload.put((byte)crc.getLSB());
    payload.put((byte)crc.getMSB());
    return payload.array();
}

@Test
public void test(){
    byte[] packet = generateTestPacket();
    for(int i = 0; i < packet.length - 1; i++){
        parser.mavlink_parse_char(packet[i] & 0xFF);
    }
    MAVLinkPacket m = parser.mavlink_parse_char(packet[packet.length - 1] & 0xFF);
    byte[] processedPacket = m.encodePacket();
    assertArrayEquals("msg_command_int", processedPacket, packet);
}
}
        