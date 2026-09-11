package com.example.minicommerce.configuration;

import com.entloom.crud.api.model.SubjectContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServletPrincipalCrudSubjectResolverTest {
    @Test
    void mapsAuthenticatedServletPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "alice");

        SubjectContext subject = new ServletPrincipalCrudSubjectResolver(provider(request)).resolveOrThrow();

        assertEquals("alice", subject.getSubjectId());
    }

    @Test
    void mapsMissingPrincipalToExplicitAnonymousSubject() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        SubjectContext subject = new ServletPrincipalCrudSubjectResolver(provider(request)).resolveOrThrow();

        assertEquals(ServletPrincipalCrudSubjectResolver.ANONYMOUS_SUBJECT_ID, subject.getSubjectId());
    }

    @Test
    void mapsMissingRequestToExplicitAnonymousSubject() {
        ObjectProvider<HttpServletRequest> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        SubjectContext subject = new ServletPrincipalCrudSubjectResolver(provider).resolveOrThrow();

        assertEquals(ServletPrincipalCrudSubjectResolver.ANONYMOUS_SUBJECT_ID, subject.getSubjectId());
    }

    private ObjectProvider<HttpServletRequest> provider(HttpServletRequest request) {
        ObjectProvider<HttpServletRequest> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(request);
        return provider;
    }
}
