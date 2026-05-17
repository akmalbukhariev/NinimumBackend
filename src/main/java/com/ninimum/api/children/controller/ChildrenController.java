package com.ninimum.api.children.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.children.service.IChildrenService;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.ChildrenDto;
import com.ninimum.api.param.AddChildParam;
import com.ninimum.api.param.DeleteChildParam;
import com.ninimum.api.param.UpdateChildParam;
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
@Tag(name = "Children", description = "Stores children information of users.")
@RequestMapping(value={"/ninimum/api/v1/children"})
public class ChildrenController extends BaseController {

    private final IChildrenService childrenService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Children"},
            summary = "1. Children list",
            description = "Returns children list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getChildren/{user_id}")
    public ResponseEntity<Object> getChildren(@PathVariable Long user_id) {
        VersionResponseResult result = null;

        try {
            List<ChildrenDto> children = this.childrenService.getChildrenByUserId(user_id);
            result = this.setResult(Result.SUCCESS, children);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ChildrenController => getChildren: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Children"},
            summary = "2. Add child",
            description = "Adds a new child profile for the user.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addChild", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addChild(@RequestBody AddChildParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.childrenService.addChild(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ChildrenController => addChild: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Children"},
            summary = "3. Update child",
            description = "Updates child profile information.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/updateChild", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> updateChild(@RequestBody UpdateChildParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.childrenService.updateChild(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ChildrenController => updateChild: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Children"},
            summary = "4. Delete child",
            description = "Deletes child profile information.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteChild", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteChild(@RequestBody DeleteChildParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.childrenService.deleteChild(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ChildrenController => deleteChild: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}