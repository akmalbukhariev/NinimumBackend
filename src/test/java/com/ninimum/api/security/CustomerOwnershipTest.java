package com.ninimum.api.security;

import com.ninimum.api.dto.UserDto;
import com.ninimum.api.user.service.IUserService;
import com.ninimum.api.order.service.IOrderService;
import com.ninimum.api.order.controller.OrderController;
import com.ninimum.api.cart.service.ICartService;
import com.ninimum.api.cart.controller.CartController;
import com.ninimum.api.address.service.IAddressService;
import com.ninimum.api.address.controller.AddressController;
import com.ninimum.api.subscription.service.ISubscriptionService;
import com.ninimum.api.subscription.controller.SubscriptionController;
import com.ninimum.api.param.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOwnershipTest {
    IUserService users=mock(IUserService.class);
    CurrentCustomer customer=new CurrentCustomer(users);
    @BeforeEach void signIn() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("phone-one",null,List.of(new SimpleGrantedAuthority("USER"))));
        var user=new UserDto();user.setId(7L);when(users.getUserByPhone("phone-one")).thenReturn(user);
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void orderReadCreateCancelAndHideIgnoreForgedUserId() throws Exception {
        var service=mock(IOrderService.class);var controller=new OrderController(service,customer);
        var list=new OrderListParam();list.setUserId(99L);controller.getOrderList(list);verify(service).getOrderList(argThat(p->p.getUserId()==7));
        var create=new CreateOrderParam();create.setUserId(99L);controller.createOrder(create);verify(service).createOrder(argThat(p->p.getUserId()==7));
        var cancel=new CancelOrderParam();cancel.setUserId(99L);controller.cancelOrder(cancel);verify(service).cancelOrder(argThat(p->p.getUserId()==7));
        var detail=new OrderDetailParam();detail.setUserId(99L);controller.deleteOrderHistory(detail);verify(service).deleteOrderHistory(argThat(p->p.getUserId()==7));
        detail.setUserId(99L);controller.getOrderDetail(detail);verify(service).getOrderDetail(argThat(p->p.getUserId()==7));
    }
    @Test void cartAddressAndSubscriptionUseSignedInCustomer() throws Exception {
        var carts=mock(ICartService.class);var cart=new AddCartParam();cart.setUser_id(99L);new CartController(carts,customer).addCart(cart);verify(carts).addCart(argThat(p->p.getUser_id()==7));
        var addresses=mock(IAddressService.class);var address=new DeleteAddressParam();address.setUserId(99L);new AddressController(addresses,customer).deleteAddress(address);verify(addresses).deleteAddress(argThat(p->p.getUserId()==7));
        var subscriptions=mock(ISubscriptionService.class);var cancel=new CancelSubscriptionParam();cancel.setUserId(99L);new SubscriptionController(subscriptions,customer).cancelSubscription(cancel);verify(subscriptions).cancelSubscription(argThat(p->p.getUserId()==7));
    }
    @Test void absentCustomerOrStaffIdentityNeverReachesOrderService() {
        var service=mock(IOrderService.class);var controller=new OrderController(service,customer);
        SecurityContextHolder.clearContext();controller.createOrder(new CreateOrderParam());verifyNoInteractions(service);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner",null,List.of(new SimpleGrantedAuthority("SUPER_ADMIN"))));
        controller.getOrderList(new OrderListParam());verifyNoInteractions(service);
    }
}
