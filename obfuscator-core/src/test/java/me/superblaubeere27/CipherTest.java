/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import me.superblaubeere27.jobf.processors.encryption.string.AESEncryptionAlgorithm;
import me.superblaubeere27.jobf.processors.encryption.string.BlowfishEncryptionAlgorithm;
import me.superblaubeere27.jobf.processors.encryption.string.DESEncryptionAlgorithm;
import me.superblaubeere27.jobf.processors.encryption.string.XOREncryptionAlgorithm;
import me.superblaubeere27.jobf.utils.StringManipulationUtils;

public class CipherTest {

    @Test
    public void testDES() {
        String encrypt = "Encryption works.";
        byte[] keyBytes = "123456 is a safe key".getBytes(StandardCharsets.UTF_8);
        String keyHex = StringManipulationUtils.bytesToHex(keyBytes);

        DESEncryptionAlgorithm algorithm = new DESEncryptionAlgorithm();

        byte[] encBytes = algorithm.encrypt(encrypt, keyBytes);
        String encHex = StringManipulationUtils.bytesToHex(encBytes);

        assertEquals(encrypt, DESEncryptionAlgorithm.decrypt(encHex, keyHex));
    }


    @Test
    public void testXOR() {
        String encrypt = "Encryption works.";
        byte[] keyBytes = "123456 is a safe key".getBytes(StandardCharsets.UTF_8);
        String keyHex = StringManipulationUtils.bytesToHex(keyBytes);

        XOREncryptionAlgorithm algorithm = new XOREncryptionAlgorithm();

        byte[] encBytes = algorithm.encrypt(encrypt, keyBytes);
        String encHex = StringManipulationUtils.bytesToHex(encBytes);

        assertEquals(encrypt, XOREncryptionAlgorithm.decrypt(encHex, keyHex));
    }

    @Test
    public void testAES() {
        String encrypt = "Encryption works.";
        byte[] keyBytes = "123456 is a safe key".getBytes(StandardCharsets.UTF_8);
        String keyHex = StringManipulationUtils.bytesToHex(keyBytes);

        AESEncryptionAlgorithm algorithm = new AESEncryptionAlgorithm();

        byte[] encBytes = algorithm.encrypt(encrypt, keyBytes);
        String encHex = StringManipulationUtils.bytesToHex(encBytes);

        assertEquals(encrypt, AESEncryptionAlgorithm.decrypt(encHex, keyHex));
    }

    @Test
    public void testBlowfish() {
        String encrypt = "Encryption works.";
        byte[] keyBytes = "123456 is a safe key".getBytes(StandardCharsets.UTF_8);
        String keyHex = StringManipulationUtils.bytesToHex(keyBytes);

        BlowfishEncryptionAlgorithm algorithm = new BlowfishEncryptionAlgorithm();

        byte[] encBytes = algorithm.encrypt(encrypt, keyBytes);
        String encHex = StringManipulationUtils.bytesToHex(encBytes);

        assertEquals(encrypt, BlowfishEncryptionAlgorithm.decrypt(encHex, keyHex));
    }

}
