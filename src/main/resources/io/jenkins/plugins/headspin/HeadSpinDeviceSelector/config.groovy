package io.jenkins.plugins.sample.HeadSpinDeviceSelector;

f = namespace(lib.FormTagLib)

f.entry(title:_("Location"), field:"location") {
    f.select()
}

f.entry(title:_("Device"), field:"device") {
    f.select()
}

f.entry(title:_("Carrier"), field:"carrier") {
    f.select()
}

f.entry(title:_("Command"), field:"testShellCommand") {
    f.textbox()
}

f.entry {
    div(align:"right") {
        input (type:"button", value:_("Add Test"), class:"repeatable-add show-if-last")
        input (type:"button", value:_("Delete Test"), class:"repeatable-delete show-if-not-only")
    }
}