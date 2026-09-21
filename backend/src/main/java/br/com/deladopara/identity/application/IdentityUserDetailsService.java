package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    public IdentityUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var account = accounts.findByEmailIgnoreCase(AccountService.normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        var authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
        return new User(account.getEmail(), account.getPasswordHash(), authorities);
    }
}
