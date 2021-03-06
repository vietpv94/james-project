/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.protocols.smtp.core.fastfail;

import java.util.Iterator;

import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;


/**
 * Abstract base class which implement GreyListing. 
 * 
 *
 */
public abstract class AbstractGreylistHandler implements RcptHook {

    /** 1 hour */
    private long tempBlockTime = 3600000;

    /** 36 days */
    private long autoWhiteListLifeTime = 3110400000L;

    /** 4 hours */
    private long unseenLifeTime = 14400000;


    private static final HookResult TO_FAST = new HookResult(HookReturnCode.DENYSOFT, SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.NETWORK_DIR_SERVER) 
            + " Temporary rejected: Reconnect to fast. Please try again later");
    private static final HookResult TEMPORARY_REJECT = new HookResult(HookReturnCode.DENYSOFT, SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.NETWORK_DIR_SERVER) 
            + " Temporary rejected: Please try again later");
    public void setUnseenLifeTime(long unseenLifeTime) {
        this.unseenLifeTime = unseenLifeTime;
    }
    
    public void setAutoWhiteListLifeTime(long autoWhiteListLifeTime) {
        this.autoWhiteListLifeTime = autoWhiteListLifeTime;
    }
    
    public void setTempBlockTime(long tempBlockTime) {
        this.tempBlockTime = tempBlockTime;
    }


    private HookResult doGreyListCheck(SMTPSession session, MailAddress senderAddress, MailAddress recipAddress) {
        String recip = "";
        String sender = "";

        if (recipAddress != null) recip = recipAddress.toString();
        if (senderAddress != null) sender = senderAddress.toString();
    
        long time = System.currentTimeMillis();
        String ipAddress = session.getRemoteAddress().getAddress().getHostAddress();
        
        try {
            long createTimeStamp = 0;
            int count = 0;
            
            // get the timestamp when he triplet was last seen
            Iterator<String> data = getGreyListData(ipAddress, sender, recip);
            
            if (data.hasNext()) {
                createTimeStamp = Long.parseLong(data.next());
                count = Integer.parseInt(data.next());
            }
            
            session.getLogger().debug("Triplet " + ipAddress + " | " + sender + " | " + recip  +" -> TimeStamp: " + createTimeStamp);


            // if the timestamp is bigger as 0 we have allready a triplet stored
            if (createTimeStamp > 0) {
                long acceptTime = createTimeStamp + tempBlockTime;
        
                if ((time < acceptTime) && (count == 0)) {
                    return TO_FAST;
                } else {
                    
                    session.getLogger().debug("Update triplet " + ipAddress + " | " + sender + " | " + recip + " -> timestamp: " + time);
                    
                    // update the triplet..
                    updateTriplet(ipAddress, sender, recip, count, time);

                }
            } else {
                session.getLogger().debug("New triplet " + ipAddress + " | " + sender + " | " + recip );
           
                // insert a new triplet
                insertTriplet(ipAddress, sender, recip, count, time);
      
                // Tempory block on new triplet!
                return TEMPORARY_REJECT;
            }

            // some kind of random cleanup process
            if (Math.random() > 0.99) {
                // cleanup old entries
            
                session.getLogger().debug("Delete old entries");
            
                cleanupAutoWhiteListGreyList(time - autoWhiteListLifeTime);
                cleanupGreyList(time - unseenLifeTime);
            }

        } catch (Exception e) {
            // just log the exception
            session.getLogger().error("Error on greylist method: " + e.getMessage());
        }
        return HookResult.declined();
    }

    /**
     * Get all necessary data for greylisting based on provided triplet
     * 
     * @param ipAddress
     *            The ipAddress of the client
     * @param sender
     *            The mailFrom
     * @param recip
     *            The rcptTo
     * @return data
     *            The data
     * @throws Exception
     */
    protected abstract  Iterator<String> getGreyListData(String ipAddress, String sender, String recip) throws Exception;

    /**
     * Insert new triplet in the store
     * 
     * @param ipAddress
     *            The ipAddress of the client
     * @param sender
     *            The mailFrom
     * @param recip
     *            The rcptTo
     * @param count
     *            The count
     * @param createTime
     *            The createTime
     * @throws SQLException
     */
    protected abstract void insertTriplet(String ipAddress, String sender, String recip, int count, long createTime)
        throws Exception;

    /**
     * Update the triplet
     * 
     * 
     * @param ipAddress
     *            The ipAddress of the client
     * @param sender
     *            The mailFrom
     * @param recip
     *            The rcptTo
     * @param count
     *            The count
     * @param time
     *            the current time in ms
     * @throws Exception
     */
    protected abstract void updateTriplet(String ipAddress, String sender, String recip, int count, long time) throws Exception;
       

    /**
     * Cleanup the autowhitelist
     * 
     * @param time
     *            The time which must be reached before delete the records
     * @throws Exception
     */
    protected abstract void cleanupAutoWhiteListGreyList(long time)throws Exception;     

    /**
     * Delete old entries from the Greylist datarecord 
     * 
     * @param time
     *            The time which must be reached before delete the records
     * @throws Exception
     */
    protected abstract void cleanupGreyList(long time) throws Exception;

  

    /**
     * @see org.apache.james.protocols.smtp.hook.RcptHook#doRcpt(org.apache.james.protocols.smtp.SMTPSession, org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        if (!session.isRelayingAllowed()) {
            return doGreyListCheck(session, sender,rcpt);
        } else {
            session.getLogger().info("IpAddress " + session.getRemoteAddress().getAddress().getHostAddress() + " is allowed to send. Skip greylisting.");
        }
        return HookResult.declined();
    }
}
