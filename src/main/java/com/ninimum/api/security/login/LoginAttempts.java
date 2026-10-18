package com.ninimum.api.security.login;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Supplier;

/** Counter transactions survive failed login/session transactions and serialize across instances. */
@Service
public class LoginAttempts {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public LoginAttempts(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public void verify(String realm,String identity,Supplier<Boolean> credentials) {
        if(!java.util.Set.of("ADMIN","WAREHOUSE","COURIER").contains(realm))throw new IllegalArgumentException("Invalid login realm");
        String key=digest(identity);
        String outcome=tx.execute(status->{
            jdbc.update("INSERT IGNORE INTO login_attempt_state(realm,identity_key,updated_at) VALUES(?,?,UTC_TIMESTAMP())",realm,key);
            var state=jdbc.queryForMap("SELECT failures,locked_until,locked_until>UTC_TIMESTAMP() blocked FROM login_attempt_state WHERE realm=? AND identity_key=? FOR UPDATE",realm,key);
            Object blocked=state.get("blocked");
            if(Boolean.TRUE.equals(blocked)||(blocked instanceof Number&&((Number)blocked).intValue()==1)) {
                audit(realm,key,"BLOCKED");return "BLOCKED";
            }
            int failures=state.get("locked_until")==null?((Number)state.get("failures")).intValue():0;
            if(credentials.get()) {
                jdbc.update("UPDATE login_attempt_state SET failures=0,locked_until=NULL,updated_at=UTC_TIMESTAMP() WHERE realm=? AND identity_key=?",realm,key);
                audit(realm,key,"SUCCESS");return "SUCCESS";
            }
            failures++;
            jdbc.update("UPDATE login_attempt_state SET failures=?,locked_until=IF(? >= 5,UTC_TIMESTAMP()+INTERVAL 15 MINUTE,NULL),updated_at=UTC_TIMESTAMP() WHERE realm=? AND identity_key=?",failures,failures,realm,key);
            String result=failures>=5?"LOCKED":"FAILED";audit(realm,key,result);return result;
        });
        // Throw after commit: a failed authentication must not roll back its attempt counter.
        if("LOCKED".equals(outcome)||"BLOCKED".equals(outcome))throw new LockedException("Too many attempts. Try again in 15 minutes.");
        if(!"SUCCESS".equals(outcome))throw new BadCredentialsException("Invalid login or password");
    }
    private void audit(String realm,String key,String outcome) {
        jdbc.update("INSERT INTO login_attempt_audit(realm,identity_key,outcome,occurred_at) VALUES(?,?,?,UTC_TIMESTAMP())",realm,key,outcome);
    }
    private static String digest(String identity) {
        String value=identity==null?"":identity.trim().toUpperCase(Locale.ROOT);
        if(value.length()>255)throw new BadCredentialsException("Invalid login or password");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
