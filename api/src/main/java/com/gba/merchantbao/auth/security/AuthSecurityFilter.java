package com.gba.merchantbao.auth.security;

import com.gba.merchantbao.auth.dto.AuthRequests;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.exception.InvalidAccessTokenException;
import com.gba.merchantbao.auth.handler.AuthHttpErrors;
import com.gba.merchantbao.auth.logging.AuthAudit;
import com.gba.merchantbao.auth.service.AccountService;
import com.gba.merchantbao.auth.service.AuthRateLimiter;
import com.gba.merchantbao.auth.service.SessionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.common.error.CommonErrorCode;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.validation.Validator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Only installed inside the v1 security chain, never as a servlet Filter bean. */
public class AuthSecurityFilter extends OncePerRequestFilter {
    public static final String RESULT=AuthSecurityFilter.class.getName()+".result";
    private final SessionService sessions;private final AuthRateLimiter rates;private final ObjectMapper json;private final Validator validator;private final AuthHttpErrors errors;
    public AuthSecurityFilter(SessionService sessions,AuthRateLimiter rates,ObjectMapper json,Validator validator,AuthHttpErrors errors){this.sessions=sessions;this.rates=rates;this.json=json;this.validator=validator;this.errors=errors;}
    public static String bearer(HttpServletRequest request){String h=request.getHeader("Authorization");return h!=null&&h.startsWith("Bearer ")?h.substring(7):null;}
    private static String path(HttpServletRequest r){return r.getRequestURI().substring(r.getContextPath().length());}
    /** Only these catalog resources are anonymous; other store resources remain protected. */
    private static final java.util.regex.Pattern PUBLIC_CATALOG = java.util.regex.Pattern.compile(
            "/api/v1/stores/[^/]+/(products(?:/[^/]+)?|skus/[^/]+)");
    public static boolean anonymous(HttpServletRequest r){
        String p=path(r);
        if(r.getMethod().equals("GET")&&(p.equals("/api/v1/health")||PUBLIC_CATALOG.matcher(p).matches()))return true;
        return r.getMethod().equals("POST")&&(p.equals("/api/v1/auth/staff/login")||p.matches("/api/v1/stores/[^/]+/auth/(sms-codes|consumer/login)"));
    }
    /** Catalog writes are merchant staff only; role checks precede body deserialization. */
    private static final java.util.regex.Pattern PRODUCT_WRITE=java.util.regex.Pattern.compile("/api/v1/(products|product-categories|skus)(/.*)?");
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        long started=System.nanoTime();Actor auditActor=null;
        response.setHeader("Cache-Control","no-store");
        try {
            if(!anonymous(request)) {
                Object principal;String role;
                String path=path(request);
                if(request.getMethod().equals("POST")&&path.equals("/api/v1/auth/refresh")) {
                    rates.refresh(request.getRemoteAddr());var body=body(request,AuthRequests.Refresh.class);
                    request.setAttribute(RESULT,sessions.refresh(body.refreshToken(),body.refreshRequestId()));principal="validated-refresh";role="CREDENTIAL_BRANCH";
                } else if(request.getMethod().equals("POST")&&path.equals("/api/v1/auth/logout")) {
                    AuthRequests.Logout body=request.getContentLengthLong()==0?new AuthRequests.Logout(null):body(request,AuthRequests.Logout.class);
                    auditActor=sessions.logout(bearer(request),body.refreshToken());principal="validated-logout";role="CREDENTIAL_BRANCH";
                } else {
                    Actor actor=sessions.authenticate(bearer(request));auditActor=actor;principal=actor;role=actor.principal().role().name();
                    // Role authorization precedes body deserialization.
                    if(path.startsWith("/api/v1/staff-accounts"))AccountService.requireRole(actor,AccountRole.MERCHANT_STAFF);
                    if(path.startsWith("/api/v1/consumer-accounts"))AccountService.requireRole(actor,AccountRole.CONSUMER);
                    if(path.equals("/api/v1/knowledge")||path.startsWith("/api/v1/knowledge/"))AccountService.requireRole(actor,AccountRole.MERCHANT_STAFF);
                    if(!request.getMethod().equals("GET")&&PRODUCT_WRITE.matcher(path).matches())AccountService.requireRole(actor,AccountRole.MERCHANT_STAFF);
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
            }
            chain.doFilter(request,response);
        } catch(AuthError e){errors.write(request,response,e);}
        catch(InvalidAccessTokenException e){errors.write(request,response,AuthError.unauthorized());}
        catch(IllegalArgumentException|org.springframework.http.converter.HttpMessageNotReadableException e){errors.write(request,response,new AuthError(400,CommonErrorCode.INVALID_ARGUMENT));}
        finally {
            try { if(!path(request).equals("/api/v1/health")) {
                Object available=request.getAttribute(AuthAudit.ACTOR);
                if(available instanceof Actor actor)auditActor=actor;
                Object code=request.getAttribute("auth.errorCode");
                AuthAudit.record(request.getMethod()+":"+path(request).replaceAll("/stores/[^/]+/","/stores/{storeSlug}/").replaceAll("/[0-9]+$","/{id}"),auditActor,code instanceof Integer i?i:(response.getStatus()<400?0:10001),(System.nanoTime()-started)/1_000_000);
            }} finally {SecurityContextHolder.clearContext();}
        }
    }
    private <T>T body(HttpServletRequest request,Class<T> type)throws IOException {
        try {T body=json.readerFor(type).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(request.getInputStream());
            if(body==null||!validator.validate(body).isEmpty())throw new IllegalArgumentException("Invalid auth request");return body;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid auth request");}
    }
}
