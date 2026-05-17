package com.ninimum.api.cart.controller;

import com.ninimum.api.cart.service.ICartService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.CartCountDto;
import com.ninimum.api.dto.CartDto;
import com.ninimum.api.param.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "mapper/Cart", description = "Shopping cart APIs.")
@RequestMapping(value={"/ninimum/api/v1/cart"})
public class CartController extends BaseController {

    private final ICartService cartService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"mapper/Cart"},
            summary = "1. Cart list",
            description = "Returns cart product list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getCartList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getCartList(@RequestBody CartListParam param) {
        VersionResponseResult result = null;

        try {
            List<CartDto> carts = this.cartService.getCartList(param);
            result = this.setResult(Result.SUCCESS, carts);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CartController => getCartList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"mapper/Cart"},
            summary = "2. Add cart",
            description = "Adds product to cart.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addCart", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addCart(@RequestBody AddCartParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.cartService.addCart(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CartController => addCart: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"mapper/Cart"},
            summary = "3. Update cart quantity",
            description = "Updates product quantity in cart.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/updateCart", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> updateCart(@RequestBody UpdateCartParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.cartService.updateCart(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CartController => updateCart: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"mapper/Cart"},
            summary = "4. Delete cart",
            description = "Deletes product from cart.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteCart", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteCart(@RequestBody DeleteCartParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.cartService.deleteCart(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CartController => deleteCart: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Cart"},
            summary = "5. Clear cart",
            description = "Deletes all products from user cart.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/clearCart", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> clearCart(@RequestBody ClearCartParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.cartService.clearCart(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CartController => clearCart: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Cart"},
            summary = "6. Cart count",
            description = "Returns cart product count by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getCartCount", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getCartCount(@RequestBody CartListParam param) {
        VersionResponseResult result = null;

        try {
            CartCountDto cartCount = this.cartService.getCartCount(param);
            result = this.setResult(Result.SUCCESS, cartCount);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CartController => getCartCount: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}