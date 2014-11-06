function $(id) {
  return document.getElementById(id);
}

/* Ignored block comment: "ignore\" me" */
function show(id) {
  $(id).style.display = "block";
}

function hide(id) {
  $(id).style.display = "none";
}
// Line comment
function onStatusBoxFocus(elt) {
  elt.value = '';
  elt.style.color = "#000";
  show('status_submit');
});

// Code which loads other HTML
$("#a").load("used_index2.html");
x = "used_index3.html";
$("#a").load(x);

// Variable names are *not* aliased
y = unused_icon;
// Comments are ignored:
//$("#a").load("unused_icon.png");