/*
 * Copyright (C) 2012 The Android Open Source Project 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package com.android.jobb;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.PBEKeySpec;

public class PBKDF {
    public static final int SALT_LEN = 8;
    private static final int ROUNDS = 1024;
    private static final int KEY_BITS = 128;
    
    public static byte[] getKey(String password, byte[] saltBytes) throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
        PBEKeySpec pwKey = new PBEKeySpec(password.toCharArray(), saltBytes, ROUNDS, KEY_BITS);  
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");  
        PBEKey pbeKey;
        try {
            pbeKey = (PBEKey) factory.generateSecret(pwKey);
            byte[] pbkdfKey = pbeKey.getEncoded();
            return pbkdfKey;
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }             
        return null;
    }
    
    public static byte[] getRandomSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[SALT_LEN];
        random.nextBytes(saltBytes);
        return saltBytes;  
    }
    
}
